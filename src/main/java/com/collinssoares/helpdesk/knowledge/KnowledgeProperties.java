package com.collinssoares.helpdesk.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param location            documents indexés au démarrage (motif de ressources Spring)
 * @param loadOnStartup       indexer {@code location} au démarrage de l'application
 * @param topK                nombre maximal de passages renvoyés par une recherche
 * @param similarityThreshold score de similarité (cosinus) minimal pour qu'un passage soit retenu
 */
@ConfigurationProperties("helpdesk.knowledge")
public record KnowledgeProperties(
        String location,
        boolean loadOnStartup,
        int topK,
        double similarityThreshold
) {
}
