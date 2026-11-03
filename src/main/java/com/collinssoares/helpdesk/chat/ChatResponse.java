package com.collinssoares.helpdesk.chat;

import java.util.List;

import com.collinssoares.helpdesk.agent.SourceVerifier.Source;
import com.collinssoares.helpdesk.ticket.TicketSummary;

/**
 * @param answer         réponse de l'agent
 * @param conversationId à renvoyer avec le message suivant pour garder le contexte
 * @param sources        procédures citées par l'agent, vérifiées contre celles réellement consultées
 * @param toolCalls      outils appelés par l'agent pendant ce message, dans l'ordre
 * @param ticket         ticket créé pendant ce message, sinon null
 */
public record ChatResponse(String answer, String conversationId, List<Source> sources, List<String> toolCalls,
                           TicketSummary ticket) {

    public ChatResponse(String answer, String conversationId) {
        this(answer, conversationId, List.of(), List.of(), null);
    }
}
