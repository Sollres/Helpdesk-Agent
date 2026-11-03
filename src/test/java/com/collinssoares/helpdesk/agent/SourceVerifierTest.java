package com.collinssoares.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;

class SourceVerifierTest {

    private final List<KnowledgeSnippet> consulted = List.of(
            new KnowledgeSnippet("Se connecter au VPN GlobalProtect", "Escalade", "vpn-connexion.md", "...", 0.7),
            new KnowledgeSnippet("Se connecter au VPN GlobalProtect", "Se connecter", "vpn-connexion.md", "...", 0.6));

    @Test
    void keepsCitedSourceThatWasConsulted() {
        var result = SourceVerifier.verify(
                "1. Redémarrez.\n\nSource : Se connecter au VPN GlobalProtect - Le VPN ne se connecte pas", consulted);

        assertThat(result.sources()).containsExactly(
                new SourceVerifier.Source("Se connecter au VPN GlobalProtect", "vpn-connexion.md"));
        assertThat(result.answer()).contains("Source :");
    }

    @Test
    void toleratesFormattingAndAccents() {
        var result = SourceVerifier.verify("**Source** : « se connecter au vpn globalprotect »", consulted);

        assertThat(result.sources()).hasSize(1);
    }

    @Test
    void removesInventedOrEmptySourceLines() {
        var result = SourceVerifier.verify(
                "Je n'ai pas trouvé de procédure.\n\nSource : Aucune procédure interne trouvée.", consulted);

        assertThat(result.sources()).isEmpty();
        assertThat(result.answer()).isEqualTo("Je n'ai pas trouvé de procédure.");
    }

    @Test
    void answerWithoutSourceLineIsUnchanged() {
        var result = SourceVerifier.verify("Bonjour !", consulted);

        assertThat(result.answer()).isEqualTo("Bonjour !");
        assertThat(result.sources()).isEmpty();
    }
}
