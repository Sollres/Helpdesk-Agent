package com.collinssoares.helpdesk.knowledge;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Recherche sémantique dans les procédures IT.
 */
@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;

    public KnowledgeBaseService(VectorStore vectorStore, KnowledgeProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * @return les passages les plus proches de la requête, du plus pertinent au moins pertinent ;
     * liste vide si aucun ne dépasse le seuil de similarité
     */
    public List<KnowledgeSnippet> search(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(properties.topK())
                .similarityThreshold(properties.similarityThreshold())
                .filterExpression("type == '" + KnowledgeIngestionService.TYPE_KB + "'")
                .build();
        return vectorStore.similaritySearch(request).stream()
                .map(KnowledgeBaseService::toSnippet)
                .toList();
    }

    private static KnowledgeSnippet toSnippet(Document document) {
        var metadata = document.getMetadata();
        return new KnowledgeSnippet(
                (String) metadata.get("title"),
                (String) metadata.get("section"),
                (String) metadata.get("source"),
                document.getText(),
                document.getScore());
    }
}
