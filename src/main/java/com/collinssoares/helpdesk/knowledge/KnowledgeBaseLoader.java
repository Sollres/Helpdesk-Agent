package com.collinssoares.helpdesk.knowledge;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Indexe au démarrage les procédures livrées avec l'application.
 * Grâce à l'empreinte des documents, seuls les fichiers nouveaux ou modifiés sont recalculés.
 */
@Component
public class KnowledgeBaseLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseLoader.class);

    private final KnowledgeIngestionService ingestionService;
    private final KnowledgeProperties properties;

    public KnowledgeBaseLoader(KnowledgeIngestionService ingestionService, KnowledgeProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.loadOnStartup()) {
            return;
        }
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(properties.location());
        Map<IngestionResult.Status, Long> summary = Arrays.stream(resources)
                .filter(r -> r.getFilename() != null && KnowledgeIngestionService.isSupported(r.getFilename()))
                .map(this::ingest)
                .collect(Collectors.groupingBy(IngestionResult::status, Collectors.counting()));
        log.info("Base de connaissances chargée depuis {} : {}", properties.location(), summary);
    }

    private IngestionResult ingest(Resource resource) {
        try {
            return ingestionService.ingest(resource.getFilename(), resource.getContentAsByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Lecture impossible : " + resource.getFilename(), e);
        }
    }
}
