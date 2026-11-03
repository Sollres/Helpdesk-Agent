package com.collinssoares.helpdesk.ticket;

import java.time.OffsetDateTime;

/**
 * Vue d'un ticket renvoyée au LLM et à l'API : sans données internes (id, demandeur).
 */
public record TicketSummary(
        String reference,
        String title,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        String resolution,
        String lastComment,
        OffsetDateTime updatedAt
) {

    static TicketSummary of(Ticket ticket, String lastComment) {
        return new TicketSummary(ticket.reference(), ticket.title(), ticket.category(), ticket.priority(),
                ticket.status(), ticket.resolution(), lastComment, ticket.updatedAt());
    }
}
