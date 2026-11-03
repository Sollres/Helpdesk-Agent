package com.collinssoares.helpdesk.ticket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Calcule au démarrage l'embedding des tickets qui n'en ont pas (historique importé par Flyway).
 */
@Component
public class TicketIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TicketIndexer.class);

    private final TicketService ticketService;

    public TicketIndexer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int indexed = ticketService.indexMissing();
        log.info("Tickets indexés au démarrage : {}", indexed);
    }
}
