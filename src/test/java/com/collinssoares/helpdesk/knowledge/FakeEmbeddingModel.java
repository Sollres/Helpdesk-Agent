package com.collinssoares.helpdesk.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Modèle d'embeddings déterministe pour les tests : « sac de mots » haché dans un vecteur normalisé.
 * Deux textes qui partagent des mots ont une similarité cosinus positive, sans appeler de vrai modèle.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 1024;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(new Embedding(vectorize(texts.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        for (String word : normalized.split("[^a-z0-9]+")) {
            if (word.length() >= 3) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0) {
            float n = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= n;
            }
        }
        return vector;
    }
}
