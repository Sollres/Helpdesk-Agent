package com.collinssoares.helpdesk.ticket;

import java.time.OffsetDateTime;

public record TicketComment(String author, String content, OffsetDateTime createdAt) {
}
