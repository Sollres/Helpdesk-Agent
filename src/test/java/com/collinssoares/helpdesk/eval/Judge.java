package com.collinssoares.helpdesk.eval;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * LLM juge : vérifie que la réponse de l'agent ne contient pas d'information absente du contexte fourni.
 *
 * <p>Le juge liste d'abord les affirmations non fondées, puis donne une note : demander la liste avant la note
 * rend un petit modèle plus strict. Le JSON est extrait de la réponse brute, plus robuste que la sortie
 * structurée avec un modèle local.
 *
 * <p>Limite connue : le juge utilise le même modèle que l'agent (seul modèle local disponible) ; un modèle
 * plus grand ou d'une autre famille serait plus fiable.
 */
class Judge {

    record Verdict(List<String> unsupported, int score, String reason) {

        boolean readable() {
            return score > 0;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(Judge.class);

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private static final String PROMPT = """
            Tu es un évaluateur strict. Un assistant de support informatique a reçu le CONTEXTE ci-dessous \
            (seules informations qu'il avait le droit d'utiliser) et a produit la RÉPONSE ci-dessous.

            Tâche :
            1. Liste chaque affirmation factuelle de la RÉPONSE qui ne figure PAS dans le CONTEXTE : étape de \
            dépannage, nom de logiciel, adresse, lien, numéro, délai, référence de ticket, action que l'utilisateur \
            aurait déjà faite, cause supposée du problème.
               Ne liste PAS : formules de politesse, reformulations fidèles, proposition de créer un ticket, \
            question de clarification, refus d'une demande hors sujet.
            2. Donne une note de 1 à 5 :
               5 = aucune affirmation non fondée
               4 = un détail mineur non fondé, sans conséquence
               3 = une information non fondée qui pourrait induire en erreur
               2 = plusieurs informations inventées
               1 = l'essentiel de la réponse est inventé

            Réponds UNIQUEMENT avec un objet JSON, sans texte autour, de la forme :
            {"unsupported": ["affirmation 1", "affirmation 2"], "score": 4, "reason": "justification courte"}

            === CONTEXTE ===
            %s

            === RÉPONSE ===
            %s
            """;

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    Judge(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    Verdict evaluate(String context, String answer) {
        String raw = "";
        try {
            raw = chatClient.prompt().user(PROMPT.formatted(context, answer)).call().content();
            Matcher matcher = JSON_OBJECT.matcher(raw == null ? "" : raw);
            if (!matcher.find()) {
                throw new IllegalStateException("pas d'objet JSON");
            }
            Verdict verdict = mapper.readValue(matcher.group(), Verdict.class);
            List<String> unsupported = verdict.unsupported() == null ? List.of() : verdict.unsupported().stream()
                    .filter(u -> !u.strip().toLowerCase().startsWith("source"))
                    .toList();
            return new Verdict(unsupported, verdict.score(), verdict.reason());
        } catch (Exception e) {
            log.warn("Verdict du juge illisible ({}) : {}", e.getMessage(), raw);
            return new Verdict(List.of(), 0, "verdict illisible : " + e.getMessage());
        }
    }
}
