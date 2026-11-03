package com.collinssoares.helpdesk.ticket;

import java.time.Year;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TicketRepository {

    private final JdbcClient jdbc;

    public TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Ticket create(String title, String description, TicketCategory category, TicketPriority priority,
                         String requester) {
        long number = jdbc.sql("SELECT nextval('ticket_reference_seq')").query(Long.class).single();
        String reference = "HD-%d-%06d".formatted(Year.now().getValue(), number);
        return jdbc.sql("""
                        INSERT INTO tickets (reference, title, description, category, priority, status, requester)
                        VALUES (:reference, :title, :description, :category, :priority, 'OPEN', :requester)
                        RETURNING *""")
                .param("reference", reference)
                .param("title", title)
                .param("description", description)
                .param("category", category.name())
                .param("priority", priority.name())
                .param("requester", requester)
                .query(Ticket.class)
                .single();
    }

    public Optional<Ticket> findByReference(String reference) {
        return jdbc.sql("SELECT * FROM tickets WHERE reference = :reference")
                .param("reference", reference)
                .query(Ticket.class)
                .optional();
    }

    public List<Ticket> findByReferences(Collection<String> references) {
        if (references.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT * FROM tickets WHERE reference IN (:references)")
                .param("references", references)
                .query(Ticket.class)
                .list();
    }

    /** Tickets les plus récemment mis à jour, éventuellement filtrés par statut. */
    public List<Ticket> findRecent(TicketStatus status, int limit) {
        return jdbc.sql("""
                        SELECT * FROM tickets
                        WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                        ORDER BY updated_at DESC, id DESC
                        LIMIT :limit""")
                .param("status", status != null ? status.name() : null)
                .param("limit", limit)
                .query(Ticket.class)
                .list();
    }

    public Optional<String> findLastComment(long ticketId) {
        return jdbc.sql("SELECT content FROM ticket_comments WHERE ticket_id = :id ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("id", ticketId)
                .query(String.class)
                .optional();
    }

    public List<TicketComment> findComments(long ticketId) {
        return jdbc.sql("SELECT author, content, created_at FROM ticket_comments WHERE ticket_id = :id ORDER BY created_at, id")
                .param("id", ticketId)
                .query(TicketComment.class)
                .list();
    }

    /** Tickets dont l'embedding n'est pas encore dans la base vectorielle. */
    public List<Ticket> findNotIndexed() {
        return jdbc.sql("""
                        SELECT * FROM tickets t
                        WHERE NOT EXISTS (SELECT 1 FROM vector_store v
                                          WHERE v.metadata ->> 'type' = 'ticket'
                                            AND v.metadata ->> 'reference' = t.reference)""")
                .query(Ticket.class)
                .list();
    }
}
