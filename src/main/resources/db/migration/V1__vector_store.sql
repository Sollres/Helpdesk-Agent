-- Base vectorielle utilisée par Spring AI PgVectorStore.
-- La dimension dépend du modèle d'embeddings (bge-m3 : 1024).
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(${embedding_dimensions})
);

CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX vector_store_source_idx ON vector_store ((metadata ->> 'source'));
