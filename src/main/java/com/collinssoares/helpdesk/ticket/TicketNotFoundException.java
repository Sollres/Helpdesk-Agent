package com.collinssoares.helpdesk.ticket;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(String reference) {
        super("Aucun ticket ne porte la référence " + reference + ". Vérifiez le numéro (format HD-AAAA-000000).");
    }
}
