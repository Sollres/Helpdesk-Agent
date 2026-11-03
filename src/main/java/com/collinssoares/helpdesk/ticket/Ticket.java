package com.collinssoares.helpdesk.ticket;

import java.time.OffsetDateTime;

public record Ticket(
        Long id,
        String reference,
        String title,
        String description,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        String requester,
        String resolution,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Texte utilisé pour calculer l'embedding du ticket (recherche de tickets similaires). */
    String embeddingText() {
        return title + "\n" + description + (resolution != null ? "\nRésolution : " + resolution : "");
    }
}
