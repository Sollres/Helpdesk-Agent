package com.collinssoares.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;
import com.collinssoares.helpdesk.ticket.TicketCategory;
import com.collinssoares.helpdesk.ticket.TicketPriority;
import com.collinssoares.helpdesk.ticket.TicketStatus;
import com.collinssoares.helpdesk.ticket.TicketSummary;

class KnowledgeContextAdvisorTest {

    @Test
    void messageContainsProceduresAndTickets() {
        var snippet = new KnowledgeSnippet("Se connecter au VPN", "Escalade", "vpn.md", "Créez un ticket.", 0.8);
        var ticket = new TicketSummary("HD-2026-000004", "Outlook bloqué", TicketCategory.SOFTWARE,
                TicketPriority.CRITICAL, TicketStatus.IN_PROGRESS, null, "Incident en cours.", null);

        String message = KnowledgeContextAdvisor.buildUserMessage("VPN en panne", List.of(snippet), List.of(ticket));

        assertThat(message).contains(
                "VPN en panne",
                "[Procédure : Se connecter au VPN]", "Créez un ticket.",
                "INCIDENTS EN COURS", "Ticket HD-2026-000004 (statut : IN_PROGRESS) — Outlook bloqué",
                "Dernier commentaire du support : Incident en cours.");
    }

    @Test
    void messageContainsTicketsReferencedByTheUser() {
        var ticket = new TicketSummary("HD-2026-000002", "Copieur HS", TicketCategory.HARDWARE,
                TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, null, "Technicien jeudi.", null);

        String message = KnowledgeContextAdvisor.buildUserMessage("Où en est HD-2026-000002 ?", List.of(), List.of(),
                List.of(ticket));

        assertThat(message).contains("Tickets dont l'utilisateur cite la référence",
                "Ticket HD-2026-000002 (statut : IN_PROGRESS) — Copieur HS", "Technicien jeudi.");
    }

    @Test
    void messageSaysWhenNothingWasFound() {
        String message = KnowledgeContextAdvisor.buildUserMessage("Recette de la tarte tatin", List.of(), List.of());

        assertThat(message).contains("AUCUNE procédure interne", "AUCUN ticket résolu similaire")
                .doesNotContain("INCIDENTS EN COURS");
    }
}
