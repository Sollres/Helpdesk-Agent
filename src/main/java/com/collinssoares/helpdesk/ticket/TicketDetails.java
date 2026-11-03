package com.collinssoares.helpdesk.ticket;

import java.time.OffsetDateTime;
import java.util.List;

public record TicketDetails(TicketSummary ticket, String description, OffsetDateTime createdAt,
                            List<TicketComment> comments) {
}
