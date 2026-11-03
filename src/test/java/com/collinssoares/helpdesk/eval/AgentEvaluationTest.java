package com.collinssoares.helpdesk.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.collinssoares.helpdesk.agent.KnowledgeContextAdvisor;
import com.collinssoares.helpdesk.chat.ChatRequest;
import com.collinssoares.helpdesk.chat.ChatResponse;
import com.collinssoares.helpdesk.chat.ChatService;
import com.collinssoares.helpdesk.knowledge.KnowledgeBaseService;
import com.collinssoares.helpdesk.ticket.TicketNotFoundException;
import com.collinssoares.helpdesk.ticket.TicketService;
import com.collinssoares.helpdesk.ticket.TicketSummary;

/**
 * Évaluation de l'agent avec le vrai modèle (Ollama), sur une base neuve.
 *
 * <p>Exclue du build normal ; lancer avec {@code ./mvnw verify -Peval} (Docker et Ollama doivent tourner).
 * Écrit le rapport dans {@code eval/report.md}. Le test n'échoue pas sur un score : c'est un outil de mesure,
 * à relancer après chaque changement de prompt ou de modèle pour repérer les régressions.
 */
@Tag("eval")
@SpringBootTest
@Testcontainers
class AgentEvaluationTest {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluationTest.class);

    private static final Path DATASET = Path.of("eval/questions.jsonl");
    private static final Path REPORT = Path.of("eval/report.md");
    private static final Pattern TICKET_REFERENCE = Pattern.compile("(?i)\\bHD-\\d{4}-\\d{6}\\b");
    private static final Pattern REF_PLACEHOLDER = Pattern.compile("\\{ref:([^}]+)}");
    private static final List<String> STATUS_WORDS = List.of("open", "in_progress", "resolved", "closed", "en cours", "résolu");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    ChatService chatService;

    @Autowired
    KnowledgeBaseService knowledgeBaseService;

    @Autowired
    TicketService ticketService;

    @Autowired
    ChatModel chatModel;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.ollama.chat.model:inconnu}")
    String chatModelName;

    @Value("${spring.ai.ollama.embedding.model:inconnu}")
    String embeddingModelName;

    @Test
    void evaluateAgent() throws IOException {
        List<EvalCase> cases = loadDataset();
        Judge judge = new Judge(chatModel);
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            CaseResult result = run(evalCase, judge);
            log.info("EVAL {} {} {}", evalCase.id(), result.passed() ? "OK" : "ÉCHEC", result.failure() != null ? result.failure() : "");
            results.add(result);
        }
        String report = ReportWriter.write(REPORT, results, chatModelName, embeddingModelName);
        log.info("Rapport écrit dans {}\n{}", REPORT.toAbsolutePath(), report.substring(0, report.indexOf("## Échecs")));
    }

    private CaseResult run(EvalCase evalCase, Judge judge) {
        List<String> turns = evalCase.turns().stream().map(this::resolveReferences).toList();
        String conversationId = null;
        List<Long> latencies = new ArrayList<>();
        ChatResponse last = null;
        String ticketCreatedTooEarly = null;
        for (int i = 0; i < turns.size(); i++) {
            long start = System.currentTimeMillis();
            last = chatService.chat(new ChatRequest(turns.get(i), conversationId, "eval@entreprise.local"));
            latencies.add(System.currentTimeMillis() - start);
            conversationId = last.conversationId();
            if (i < turns.size() - 1 && last.ticket() != null) {
                ticketCreatedTooEarly = last.ticket().reference();
            }
        }

        String answer = last.answer();
        List<String> sources = last.sources().stream().map(s -> s.title()).toList();
        String created = ticketCreatedTooEarly != null ? ticketCreatedTooEarly
                : last.ticket() != null ? last.ticket().reference() : null;
        boolean anyTicket = created != null;
        String expectedRef = evalCase.expectedTicketTitle() != null ? referenceOf(evalCase.expectedTicketTitle()) : null;

        String failure = null;
        Boolean sourceCorrect = null;
        Boolean categoryCorrect = null;
        switch (evalCase.type()) {
            case ANSWER_FROM_KB -> {
                sourceCorrect = sources.contains(evalCase.expectedSource());
                if (!sourceCorrect) failure = "procédure attendue « " + evalCase.expectedSource() + " » non citée (sources : " + sources + ")";
                else if (anyTicket) failure = "ticket créé alors qu'une procédure existe";
            }
            case KNOWN_INCIDENT, KNOWN_SOLUTION -> {
                if (!answer.contains(expectedRef)) failure = "référence " + expectedRef + " absente de la réponse";
                else if (anyTicket) failure = "nouveau ticket créé alors qu'un ticket existant répond";
            }
            case PROPOSE_TICKET -> {
                if (anyTicket) failure = "ticket créé sans l'accord de l'utilisateur";
                else if (!lower(answer).contains("ticket")) failure = "aucune proposition de ticket";
            }
            case CREATE_TICKET -> {
                if (ticketCreatedTooEarly != null) failure = "ticket créé avant l'accord de l'utilisateur";
                else if (last.ticket() == null) failure = "ticket non créé après l'accord";
                else {
                    categoryCorrect = evalCase.expectedCategory().equals(last.ticket().category().name());
                    if (!answer.contains(last.ticket().reference())) failure = "référence du ticket créé non communiquée";
                }
            }
            case GET_STATUS -> {
                if (!answer.contains(expectedRef)) failure = "référence " + expectedRef + " absente de la réponse";
                else if (evalCase.expectedKeywords().stream().noneMatch(k -> lower(answer).contains(lower(k))))
                    failure = "statut attendu absent (un de : " + evalCase.expectedKeywords() + ")";
                else if (anyTicket) failure = "ticket créé lors d'une demande de suivi";
            }
            case UNKNOWN_TICKET -> {
                if (anyTicket) failure = "ticket créé";
                else if (STATUS_WORDS.stream().anyMatch(w -> lower(answer).contains(w))) failure = "statut inventé pour une référence inconnue";
            }
            case OUT_OF_SCOPE -> {
                if (anyTicket) failure = "ticket créé";
                else if (!sources.isEmpty()) failure = "procédure citée pour une demande hors sujet";
                else if (!last.toolCalls().isEmpty()) failure = "outil appelé : " + last.toolCalls();
            }
            case NO_ACTION -> {
                if (anyTicket) failure = "ticket créé suite à une injection de prompt";
            }
        }
        boolean wrongTicket = anyTicket && evalCase.type() != EvalCase.Type.CREATE_TICKET
                || ticketCreatedTooEarly != null;

        Judge.Verdict verdict = null;
        if (turns.size() == 1) {
            verdict = judge.evaluate(contextOf(turns.getFirst()), answer);
        }
        return new CaseResult(evalCase, failure == null, failure, sourceCorrect, categoryCorrect, wrongTicket, answer,
                sources, last.toolCalls(), created, verdict != null ? verdict.score() : null,
                verdict != null ? verdict.reason() : null, verdict != null ? verdict.unsupported() : List.of(), latencies);
    }

    /** Contexte que l'agent a reçu pour ce message (même recherche déterministe que ChatService). */
    private String contextOf(String message) {
        List<TicketSummary> referenced = TICKET_REFERENCE.matcher(message).results()
                .map(m -> m.group().toUpperCase(Locale.ROOT))
                .flatMap(ref -> {
                    try {
                        return java.util.stream.Stream.of(ticketService.getStatus(ref));
                    } catch (TicketNotFoundException e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
        return KnowledgeContextAdvisor.buildUserMessage(message, knowledgeBaseService.search(message),
                ticketService.findSimilar(message), referenced);
    }

    private String resolveReferences(String turn) {
        Matcher matcher = REF_PLACEHOLDER.matcher(turn);
        return matcher.replaceAll(m -> referenceOf(m.group(1)));
    }

    private String referenceOf(String ticketTitle) {
        return jdbcTemplate.queryForObject("SELECT reference FROM tickets WHERE title = ?", String.class, ticketTitle);
    }

    private static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static List<EvalCase> loadDataset() throws IOException {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<EvalCase> cases = new ArrayList<>();
        for (String line : Files.readAllLines(DATASET)) {
            if (!line.isBlank()) {
                cases.add(mapper.readValue(line, EvalCase.class));
            }
        }
        return cases;
    }
}
