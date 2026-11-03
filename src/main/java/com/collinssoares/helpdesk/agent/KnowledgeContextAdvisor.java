package com.collinssoares.helpdesk.agent;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.stereotype.Component;

import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;
import com.collinssoares.helpdesk.ticket.TicketStatus;
import com.collinssoares.helpdesk.ticket.TicketSummary;

/**
 * Ajoute au message de l'utilisateur les procédures et les tickets similaires trouvés par la
 * recherche systématique.
 *
 * <p>Placé après l'advisor de mémoire et avant la boucle d'appel d'outils : la mémoire de la
 * conversation garde le message d'origine, sans ce contexte, qui alourdirait l'historique.
 */
@Component
public class KnowledgeContextAdvisor implements BaseAdvisor {

    /** Clé du contexte de l'advisor contenant la liste des {@link KnowledgeSnippet}. */
    public static final String SNIPPETS_KEY = "helpdesk.snippets";

    /** Clé du contexte de l'advisor contenant la liste des {@link TicketSummary} similaires. */
    public static final String TICKETS_KEY = "helpdesk.tickets";

    /** Clé du contexte de l'advisor contenant les tickets dont l'utilisateur cite la référence. */
    public static final String REFERENCED_TICKETS_KEY = "helpdesk.referencedTickets";

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        if (!(request.context().get(SNIPPETS_KEY) instanceof List<?> snippets)
                || !(request.context().get(TICKETS_KEY) instanceof List<?> tickets)) {
            return request;
        }
        String question = request.prompt().getUserMessage().getText();
        List<?> referenced = request.context().get(REFERENCED_TICKETS_KEY) instanceof List<?> l ? l : List.of();
        @SuppressWarnings("unchecked")
        String augmented = buildUserMessage(question, (List<KnowledgeSnippet>) snippets, (List<TicketSummary>) tickets,
                (List<TicketSummary>) referenced);
        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(augmented))
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        // Entre MessageChatMemoryAdvisor (+200) et ToolCallingAdvisor (+300)
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50;
    }

    static String buildUserMessage(String question, List<KnowledgeSnippet> snippets, List<TicketSummary> tickets) {
        return buildUserMessage(question, snippets, tickets, List.of());
    }

    public static String buildUserMessage(String question, List<KnowledgeSnippet> snippets, List<TicketSummary> tickets,
                                          List<TicketSummary> referencedTickets) {
        String procedures = snippets.isEmpty()
                ? "AUCUNE procédure interne ne correspond à cette demande."
                : snippets.stream()
                        .map(s -> "[Procédure : " + s.title() + "]\n" + s.content())
                        .collect(Collectors.joining("\n---\n"));
        // Les incidents en cours sont présentés à part et en premier : sinon le modèle préfère la procédure,
        // même quand un incident ouvert explique le problème (constaté à l'évaluation).
        List<TicketSummary> ongoing = tickets.stream().filter(KnowledgeContextAdvisor::isOngoing).toList();
        List<TicketSummary> others = tickets.stream().filter(t -> !isOngoing(t)).toList();
        String incidents = ongoing.isEmpty()
                ? ""
                : "\n=== INCIDENTS EN COURS similaires (à signaler en priorité s'ils correspondent au symptôme) ===\n"
                        + ongoing.stream().map(KnowledgeContextAdvisor::formatTicket).collect(Collectors.joining("\n---\n"))
                        + "\n";
        String similarTickets = others.isEmpty()
                ? "AUCUN ticket résolu similaire."
                : others.stream()
                        .map(KnowledgeContextAdvisor::formatTicket)
                        .collect(Collectors.joining("\n---\n"));
        String referenced = referencedTickets.isEmpty()
                ? ""
                : "\n=== Tickets dont l'utilisateur cite la référence (statut à jour) ===\n"
                        + referencedTickets.stream()
                                .map(KnowledgeContextAdvisor::formatTicket)
                                .collect(Collectors.joining("\n---\n"))
                        + "\n";
        return """
                Demande de l'utilisateur :
                %s
                %s%s
                === Procédures internes trouvées automatiquement (elles peuvent être hors sujet, à toi de juger) ===
                %s

                === Tickets résolus similaires (ils peuvent être hors sujet, à toi de juger) ===
                %s
                """.formatted(question, referenced, incidents, procedures, similarTickets);
    }

    private static boolean isOngoing(TicketSummary t) {
        return t.status() == TicketStatus.OPEN || t.status() == TicketStatus.IN_PROGRESS;
    }

    private static String formatTicket(TicketSummary t) {
        StringBuilder text = new StringBuilder("Ticket " + t.reference() + " (statut : " + t.status() + ") — " + t.title());
        if (t.resolution() != null) {
            text.append("\nRésolution : ").append(t.resolution());
        }
        if (t.lastComment() != null) {
            text.append("\nDernier commentaire du support : ").append(t.lastComment());
        }
        return text.toString();
    }
}
