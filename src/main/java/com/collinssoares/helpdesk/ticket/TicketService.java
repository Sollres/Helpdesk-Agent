package com.collinssoares.helpdesk.ticket;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    static final String TYPE_TICKET = "ticket";

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository repository;
    private final VectorStore vectorStore;
    private final TicketProperties properties;

    public TicketService(TicketRepository repository, VectorStore vectorStore, TicketProperties properties) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public TicketSummary create(String title, String description, TicketCategory category, TicketPriority priority,
                                String requester) {
        Ticket ticket = repository.create(title.strip(), description.strip(), category, priority, requester);
        index(List.of(ticket));
        log.info("Ticket créé reference={} category={} priority={}", ticket.reference(), category, priority);
        return TicketSummary.of(ticket, null);
    }

    public TicketSummary getStatus(String reference) {
        Ticket ticket = find(reference);
        return TicketSummary.of(ticket, repository.findLastComment(ticket.id()).orElse(null));
    }

    public TicketDetails getDetails(String reference) {
        Ticket ticket = find(reference);
        return new TicketDetails(TicketSummary.of(ticket, repository.findLastComment(ticket.id()).orElse(null)),
                ticket.description(), ticket.createdAt(), repository.findComments(ticket.id()));
    }

    /** Tickets les plus récents (au plus 200), éventuellement filtrés par statut. */
    public List<TicketSummary> list(TicketStatus status) {
        return repository.findRecent(status, 200).stream()
                .map(t -> TicketSummary.of(t, repository.findLastComment(t.id()).orElse(null)))
                .toList();
    }

    /**
     * @return les tickets les plus proches de la description, du plus similaire au moins similaire
     */
    public List<TicketSummary> findSimilar(String description) {
        List<String> references = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(description)
                        .topK(properties.topK())
                        .similarityThreshold(properties.similarityThreshold())
                        .filterExpression("type == '" + TYPE_TICKET + "'")
                        .build())
                .stream()
                .map(d -> (String) d.getMetadata().get("reference"))
                .toList();

        Map<String, Ticket> tickets = repository.findByReferences(references).stream()
                .collect(Collectors.toMap(Ticket::reference, Function.identity()));
        return references.stream()
                .filter(tickets::containsKey)
                .map(tickets::get)
                .map(t -> TicketSummary.of(t, repository.findLastComment(t.id()).orElse(null)))
                .toList();
    }

    /** Calcule l'embedding des tickets qui n'en ont pas encore (tickets importés par migration, par exemple). */
    public int indexMissing() {
        List<Ticket> tickets = repository.findNotIndexed();
        index(tickets);
        return tickets.size();
    }

    private void index(List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return;
        }
        vectorStore.add(tickets.stream()
                .map(t -> new Document(t.embeddingText(), Map.of("type", TYPE_TICKET, "reference", t.reference())))
                .toList());
    }

    private Ticket find(String reference) {
        String normalized = reference.strip().toUpperCase(Locale.ROOT);
        return repository.findByReference(normalized).orElseThrow(() -> new TicketNotFoundException(normalized));
    }
}
