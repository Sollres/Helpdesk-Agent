package com.collinssoares.helpdesk.ticket;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Liste des tickets, du plus récemment mis à jour au plus ancien ; filtre facultatif par statut. */
    @GetMapping
    public List<TicketSummary> list(@RequestParam(required = false) TicketStatus status) {
        return ticketService.list(status);
    }

    @GetMapping("/{reference}")
    public TicketDetails get(@PathVariable String reference) {
        return ticketService.getDetails(reference);
    }
}
