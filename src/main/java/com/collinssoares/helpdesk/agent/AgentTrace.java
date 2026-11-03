package com.collinssoares.helpdesk.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ToolContext;

import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;
import com.collinssoares.helpdesk.ticket.TicketSummary;

/**
 * Ce qui s'est passé pendant le traitement d'un message : outils appelés, passages consultés, ticket créé.
 * Une instance par requête, transmise aux outils via le {@link ToolContext}.
 */
public class AgentTrace {

    public static final String TOOL_CONTEXT_KEY = "helpdesk.trace";

    /** Nombre maximal d'appels d'outils par message : borne le coût et évite les boucles. */
    static final int MAX_TOOL_CALLS = 5;

    /** Réponse affirmative de l'utilisateur, condition pour créer un ticket. */
    private static final Pattern CONFIRMATION = Pattern.compile(
            "(?iU)\\b(oui|ouais|ok|okay|d'accord|daccord|vas-y|vas y|allez-y|go|yes|bien sûr|bien sur|volontiers|"
                    + "je confirme|confirmé|crée|créer|créez|ouvre|ouvrez)\\b");

    private final List<String> toolCalls = new CopyOnWriteArrayList<>();
    private final List<KnowledgeSnippet> snippets = new CopyOnWriteArrayList<>();
    private final AtomicReference<TicketSummary> createdTicket = new AtomicReference<>();
    private final String requester;
    private final String userMessage;

    /**
     * @param requester   salarié à l'origine de la conversation
     * @param userMessage message en cours de traitement ; null hors d'une conversation
     */
    public AgentTrace(String requester, String userMessage) {
        this.requester = requester;
        this.userMessage = userMessage;
    }

    /**
     * Trace de la requête en cours. Si l'outil est appelé hors d'une conversation (test, appel direct),
     * renvoie une trace anonyme.
     */
    public static AgentTrace from(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get(TOOL_CONTEXT_KEY) instanceof AgentTrace trace) {
            return trace;
        }
        return new AgentTrace("inconnu", null);
    }

    /**
     * Enregistre l'appel d'un outil.
     *
     * @throws IllegalStateException si la limite d'appels est atteinte : Spring AI renvoie alors
     *                               le message d'erreur au LLM, qui doit conclure sa réponse
     */
    public void recordToolCall(String toolName) {
        if (toolCalls.size() >= MAX_TOOL_CALLS) {
            throw new IllegalStateException("Limite de " + MAX_TOOL_CALLS
                    + " appels d'outils atteinte pour ce message : réponds à l'utilisateur avec les informations déjà obtenues.");
        }
        toolCalls.add(toolName);
    }

    public void addSnippets(List<KnowledgeSnippet> found) {
        snippets.addAll(found);
    }

    /** @return false si un ticket a déjà été créé pendant ce message */
    public boolean recordCreatedTicket(TicketSummary ticket) {
        return createdTicket.compareAndSet(null, ticket);
    }

    /**
     * Garde-fou déterministe : le prompt demande au LLM d'attendre l'accord de l'utilisateur avant de
     * créer un ticket, mais un petit modèle peut l'ignorer. On vérifie donc que le message en cours
     * est bien une confirmation.
     */
    public boolean userConfirmed() {
        return userMessage == null || CONFIRMATION.matcher(userMessage).find();
    }

    public boolean hasCreatedTicket() {
        return createdTicket.get() != null;
    }

    public List<String> toolCalls() {
        return List.copyOf(toolCalls);
    }

    public List<KnowledgeSnippet> snippets() {
        return List.copyOf(snippets);
    }

    public TicketSummary createdTicket() {
        return createdTicket.get();
    }

    public String requester() {
        return requester;
    }
}
