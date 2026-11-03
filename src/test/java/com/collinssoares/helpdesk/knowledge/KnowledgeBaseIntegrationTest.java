package com.collinssoares.helpdesk.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.collinssoares.helpdesk.AbstractIntegrationTest;

/**
 * Ingestion et recherche dans les procédures, sur un vrai pgvector.
 */
class KnowledgeBaseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    KnowledgeBaseService knowledgeBaseService;

    @Autowired
    KnowledgeIngestionService ingestionService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void indexesBundledProceduresOnStartup() {
        Integer sources = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT metadata ->> 'source') FROM vector_store WHERE metadata ->> 'type' = 'kb'",
                Integer.class);

        assertThat(sources).isGreaterThanOrEqualTo(10);
    }

    @Test
    void findsTheRelevantProcedure() {
        List<KnowledgeSnippet> results = knowledgeBaseService.search("Le VPN GlobalProtect ne se connecte pas");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().source()).isEqualTo("vpn-connexion.md");
        assertThat(results.getFirst().title()).isEqualTo("Se connecter au VPN GlobalProtect");
    }

    @Test
    void searchReturnsProceduresOnlyNotTickets() {
        List<KnowledgeSnippet> results = knowledgeBaseService.search("Copieur du 2e étage hors service");

        assertThat(results).allSatisfy(s -> assertThat(s.source()).endsWith(".md"));
    }

    @Test
    void reindexesOnlyWhenContentChanges() {
        byte[] v1 = "# Procédure test\n\n## Étape\n\nRedémarrer le routeur.".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "# Procédure test\n\n## Étape A\n\nTexte A.\n\n## Étape B\n\nTexte B.".getBytes(StandardCharsets.UTF_8);

        assertThat(ingestionService.ingest("test.md", v1).status()).isEqualTo(IngestionResult.Status.INDEXED);
        assertThat(ingestionService.ingest("test.md", v1).status()).isEqualTo(IngestionResult.Status.UNCHANGED);
        assertThat(ingestionService.ingest("test.md", v2).chunks()).isEqualTo(2);

        Integer chunks = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata ->> 'source' = 'test.md'", Integer.class);
        assertThat(chunks).isEqualTo(2);
    }
}
