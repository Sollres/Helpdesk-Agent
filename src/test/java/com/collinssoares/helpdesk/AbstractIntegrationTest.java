package com.collinssoares.helpdesk;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.collinssoares.helpdesk.knowledge.FakeEmbeddingModel;

/**
 * Base des tests d'intégration : vrai PostgreSQL + pgvector (Testcontainers), modèle d'embeddings
 * factice et modèle de chat simulé. Aucun appel à Ollama ni à OpenAI.
 *
 * <p>Le conteneur est partagé par toutes les classes de test (et le contexte Spring mis en cache).
 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "helpdesk.knowledge.similarity-threshold=0.1",
        "helpdesk.tickets.similarity-threshold=0.1"
})
@Import(AbstractIntegrationTest.FakeModels.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @MockitoBean
    protected ChatModel chatModel;

    @TestConfiguration
    static class FakeModels {
        @Bean
        EmbeddingModel embeddingModel() {
            return new FakeEmbeddingModel();
        }
    }
}
