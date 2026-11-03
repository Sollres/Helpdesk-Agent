package com.collinssoares.helpdesk.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.collinssoares.helpdesk.agent.AgentTrace;
import com.collinssoares.helpdesk.knowledge.KnowledgeBaseService;
import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;

/**
 * Outil exposé au LLM pour interroger les procédures IT internes.
 */
@Component
public class KnowledgeBaseTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTools.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseTools(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Tool(name = "searchKnowledgeBase", description = """
            Recherche dans les procédures informatiques internes de l'entreprise \
            (mots de passe, VPN, Wi-Fi, messagerie, imprimantes, logiciels, sécurité...). \
            Renvoie les passages les plus pertinents avec le titre de la procédure. \
            Une liste vide signifie qu'aucune procédure ne correspond.""")
    public List<KnowledgeSnippet> searchKnowledgeBase(
            @ToolParam(description = "Le problème de l'utilisateur, formulé comme une requête de recherche courte")
            String query,
            ToolContext toolContext) {
        AgentTrace trace = AgentTrace.from(toolContext);
        trace.recordToolCall("searchKnowledgeBase");
        long start = System.currentTimeMillis();
        List<KnowledgeSnippet> results = knowledgeBaseService.search(query);
        trace.addSnippets(results);
        log.info("tool=searchKnowledgeBase query=\"{}\" results={} durationMs={}",
                query, results.size(), System.currentTimeMillis() - start);
        return results;
    }
}
