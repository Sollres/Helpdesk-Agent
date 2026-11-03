package com.collinssoares.helpdesk.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownSectionSplitterTest {

    @Test
    void splitsOnLevelTwoHeadingsAndKeepsLists() {
        String markdown = """
                # Se connecter au VPN

                Le VPN est obligatoire hors des bureaux.

                ## Se connecter

                1. Cliquez sur l'icône.
                2. Validez la notification MFA.

                ## Escalade

                Créez un ticket.
                """;

        var parsed = MarkdownSectionSplitter.split(markdown, "fallback");

        assertThat(parsed.title()).isEqualTo("Se connecter au VPN");
        assertThat(parsed.sections()).extracting(MarkdownSectionSplitter.Section::heading)
                .containsExactly("Se connecter au VPN", "Se connecter", "Escalade");
        assertThat(parsed.sections().get(1).body())
                .isEqualTo("1. Cliquez sur l'icône.\n2. Validez la notification MFA.");
    }

    @Test
    void usesDefaultTitleAndSkipsEmptySections() {
        String markdown = """
                ## Vide

                ## Contenu

                Du texte.
                """;

        var parsed = MarkdownSectionSplitter.split(markdown, "procedure vpn");

        assertThat(parsed.title()).isEqualTo("procedure vpn");
        assertThat(parsed.sections()).extracting(MarkdownSectionSplitter.Section::heading)
                .containsExactly("Contenu");
    }
}
