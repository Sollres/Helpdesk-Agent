package com.collinssoares.helpdesk.chat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import com.collinssoares.helpdesk.agent.AgentTrace;
import com.collinssoares.helpdesk.agent.KnowledgeContextAdvisor;
import com.collinssoares.helpdesk.agent.SourceVerifier;
import com.collinssoares.helpdesk.knowledge.KnowledgeBaseService;
import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;
import com.collinssoares.helpdesk.ticket.TicketNotFoundException;
import com.collinssoares.helpdesk.ticket.TicketService;
import com.collinssoares.helpdesk.ticket.TicketSummary;

@Service
public class ChatService {

    static final String DEFAULT_USER = "salarie.demo@entreprise.local";

    private static final Pattern TICKET_REFERENCE = Pattern.compile("(?i)\\bHD-\\d{4}-\\d{6}\\b");

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TicketService ticketService;

    public ChatService(ChatClient chatClient, KnowledgeBaseService knowledgeBaseService, TicketService ticketService) {
        this.chatClient = chatClient;
        this.knowledgeBaseService = knowledgeBaseService;
        this.ticketService = ticketService;
    }

    public ChatResponse chat(ChatRequest request) {
        String conversationId = request.conversationId() != null
                ? request.conversationId()
                : UUID.randomUUID().toString();
        String user = request.user() != null && !request.user().isBlank() ? request.user() : DEFAULT_USER;
        AgentTrace trace = new AgentTrace(user, request.message());

        long start = System.currentTimeMillis();
        // Recherche systématique (procédures et tickets) avant l'appel au LLM : les modèles locaux
        // de 7-8B n'appellent pas les outils de recherche de façon fiable et répondent alors de tête.
        // Le LLM garde les décisions et les actions : choisir la réponse, créer un ticket, suivre un statut.
        List<KnowledgeSnippet> snippets = knowledgeBaseService.search(request.message());
        List<TicketSummary> similarTickets = ticketService.findSimilar(request.message());
        List<TicketSummary> referencedTickets = referencedTickets(request.message());
        trace.addSnippets(snippets);

        String answer = chatClient.prompt()
                .user(request.message())
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(KnowledgeContextAdvisor.SNIPPETS_KEY, snippets)
                        .param(KnowledgeContextAdvisor.TICKETS_KEY, similarTickets)
                        .param(KnowledgeContextAdvisor.REFERENCED_TICKETS_KEY, referencedTickets))
                .toolContext(Map.of(AgentTrace.TOOL_CONTEXT_KEY, trace))
                .call()
                .content();

        SourceVerifier.VerifiedAnswer verified = SourceVerifier.verify(answer, trace.snippets());
        log.info("chat conversationId={} snippets={} similarTickets={} tools={} sources={} ticket={} durationMs={}",
                conversationId, snippets.size(), similarTickets.size(), trace.toolCalls(), verified.sources().size(),
                trace.createdTicket() != null ? trace.createdTicket().reference() : null,
                System.currentTimeMillis() - start);

        return new ChatResponse(verified.answer(), conversationId, verified.sources(), trace.toolCalls(),
                trace.createdTicket());
    }

    /** Tickets dont la référence figure dans le message : leur statut est fourni au LLM sans attendre un appel d'outil. */
    private List<TicketSummary> referencedTickets(String message) {
        return TICKET_REFERENCE.matcher(message).results()
                .map(m -> m.group().toUpperCase())
                .distinct()
                .limit(3)
                .map(this::findTicket)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<TicketSummary> findTicket(String reference) {
        try {
            return Optional.of(ticketService.getStatus(reference));
        } catch (TicketNotFoundException e) {
            return Optional.empty();
        }
    }
}
