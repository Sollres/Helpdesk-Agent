package com.collinssoares.helpdesk.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Découpe une procédure Markdown en sections de niveau 2 (« ## »), sans altérer le texte.
 *
 * <p>Le MarkdownDocumentReader de Spring AI aplatit les listes sur une seule ligne :
 * pour des procédures pas à pas, on perdrait la numérotation des étapes. Ici, chaque
 * section garde son Markdown d'origine.
 */
final class MarkdownSectionSplitter {

    record Section(String heading, String body) {
    }

    record ParsedMarkdown(String title, List<Section> sections) {
    }

    private MarkdownSectionSplitter() {
    }

    /**
     * @param markdown     contenu du fichier
     * @param defaultTitle titre utilisé si le document n'a pas de titre de niveau 1
     */
    static ParsedMarkdown split(String markdown, String defaultTitle) {
        String title = defaultTitle;
        List<Section> sections = new ArrayList<>();
        String currentHeading = null;
        StringBuilder currentBody = new StringBuilder();
        boolean titleFound = false;

        for (String line : markdown.split("\\R")) {
            if (!titleFound && line.startsWith("# ")) {
                title = line.substring(2).strip();
                titleFound = true;
            } else if (line.startsWith("## ")) {
                addSection(sections, currentHeading, currentBody);
                currentHeading = line.substring(3).strip();
                currentBody.setLength(0);
            } else {
                currentBody.append(line).append('\n');
            }
        }
        addSection(sections, currentHeading, currentBody);

        // L'introduction (avant le premier « ## ») prend le titre du document comme intitulé
        String docTitle = title;
        List<Section> named = sections.stream()
                .map(s -> s.heading() == null ? new Section(docTitle, s.body()) : s)
                .toList();
        return new ParsedMarkdown(title, named);
    }

    private static void addSection(List<Section> sections, String heading, StringBuilder body) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            sections.add(new Section(heading, text));
        }
    }
}
