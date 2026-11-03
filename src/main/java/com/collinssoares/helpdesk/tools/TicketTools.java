package com.collinssoares.helpdesk.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.collinssoares.helpdesk.agent.AgentTrace;
import com.collinssoares.helpdesk.ticket.TicketCategory;
import com.collinssoares.helpdesk.ticket.TicketPriority;
import com.collinssoares.helpdesk.ticket.TicketService;
import com.collinssoares.helpdesk.ticket.TicketSummary;

/**
 * Outils exposés au LLM pour consulter et créer des tickets de support.
 */
@Component
public class TicketTools {

    private static final Logger log = LoggerFactory.getLogger(TicketTools.class);

    private final TicketService ticketService;

    public TicketTools(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Tool(name = "findSimilarTickets", description = """
            Cherche des tickets de support existants proches du problème décrit : incident déjà connu, \
            panne en cours (statut OPEN ou IN_PROGRESS) ou problème déjà résolu (le champ resolution contient \
            alors la solution). Une liste vide signifie qu'aucun ticket similaire n'existe.""")
    public List<TicketSummary> findSimilarTickets(
            @ToolParam(description = "Description du problème de l'utilisateur") String description,
            ToolContext toolContext) {
        AgentTrace.from(toolContext).recordToolCall("findSimilarTickets");
        List<TicketSummary> results = ticketService.findSimilar(description);
        log.info("tool=findSimilarTickets results={}", results.size());
        return results;
    }

    @Tool(name = "createTicket", description = """
            Crée un ticket de support. À n'utiliser QUE si aucune procédure ni aucun ticket existant ne résout \
            le problème, ET après que l'utilisateur a explicitement accepté la création du ticket. \
            Renvoie le ticket créé, avec sa référence à communiquer à l'utilisateur.""")
    public TicketSummary createTicket(
            @ToolParam(description = "Titre court et précis du problème (moins de 100 caractères)") String title,
            @ToolParam(description = "Description complète rédigée UNIQUEMENT à partir de ce que l'utilisateur a dit : "
                    + "symptômes, messages d'erreur, ce qu'il a déjà essayé. N'ajoute aucun fait qu'il n'a pas mentionné.")
            String description,
            @ToolParam(description = "Catégorie : ACCESS (comptes, droits), NETWORK (VPN, Wi-Fi, réseau), "
                    + "HARDWARE (poste, écran, imprimante), SOFTWARE (logiciels, messagerie), OTHER")
            TicketCategory category,
            @ToolParam(description = "Priorité : LOW (gêne mineure), MEDIUM (gêne le travail), "
                    + "HIGH (bloque le travail d'une personne), CRITICAL (sécurité ou plusieurs personnes bloquées)")
            TicketPriority priority,
            ToolContext toolContext) {
        AgentTrace trace = AgentTrace.from(toolContext);
        trace.recordToolCall("createTicket");
        if (trace.hasCreatedTicket()) {
            throw new IllegalStateException("Un ticket a déjà été créé pour ce message ("
                    + trace.createdTicket().reference() + ") : n'en crée pas un second.");
        }
        if (!trace.userConfirmed()) {
            throw new IllegalStateException("Ticket NON créé : l'utilisateur n'a pas encore donné son accord. "
                    + "Résume le problème et demande-lui s'il veut que tu crées un ticket.");
        }
        if (title == null || title.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Le titre et la description du ticket sont obligatoires.");
        }
        TicketSummary ticket = ticketService.create(truncate(title, 200), description, category, priority,
                trace.requester());
        trace.recordCreatedTicket(ticket);
        log.info("tool=createTicket reference={}", ticket.reference());
        return ticket;
    }

    @Tool(name = "getTicketStatus", description = """
            Donne le statut d'un ticket à partir de sa référence (format HD-AAAA-000000) : statut, \
            date de dernière mise à jour, dernier commentaire du support et résolution éventuelle.""")
    public TicketSummary getTicketStatus(
            @ToolParam(description = "Référence du ticket, par exemple HD-2026-000004") String reference,
            ToolContext toolContext) {
        AgentTrace.from(toolContext).recordToolCall("getTicketStatus");
        log.info("tool=getTicketStatus reference={}", reference);
        return ticketService.getStatus(reference);
    }

    private static String truncate(String text, int max) {
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}
