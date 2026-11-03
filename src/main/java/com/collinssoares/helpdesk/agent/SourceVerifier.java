package com.collinssoares.helpdesk.agent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;

/**
 * Vérifie les sources citées par le LLM (ligne « Source : ... ») contre les procédures réellement
 * consultées. Seules les sources vérifiées sont renvoyées ; une ligne « Source » qui ne cite aucune
 * procédure consultée (source inventée, « Aucune procédure »...) est retirée de la réponse.
 */
public final class SourceVerifier {

    public record Source(String title, String file) {
    }

    public record VerifiedAnswer(String answer, List<Source> sources) {
    }

    private static final Pattern SOURCE_LINE = Pattern.compile("(?im)^[ \\t*_>-]*sources?[ \\t*_]*:(.*)$");

    private SourceVerifier() {
    }

    public static VerifiedAnswer verify(String answer, List<KnowledgeSnippet> consulted) {
        if (answer == null) {
            return new VerifiedAnswer("", List.of());
        }
        Set<Source> sources = new LinkedHashSet<>();
        List<String> invalidLines = new ArrayList<>();

        Matcher matcher = SOURCE_LINE.matcher(answer);
        while (matcher.find()) {
            String cited = normalize(matcher.group(1));
            List<Source> matched = consulted.stream()
                    .filter(s -> s.title() != null && cited.contains(normalize(s.title())))
                    .map(s -> new Source(s.title(), s.source()))
                    .distinct()
                    .toList();
            if (matched.isEmpty()) {
                invalidLines.add(matcher.group());
            }
            sources.addAll(matched);
        }

        String cleaned = answer;
        for (String line : invalidLines) {
            cleaned = cleaned.replace(line, "");
        }
        return new VerifiedAnswer(cleaned.strip(), List.copyOf(sources));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[«»\"'’*_]", "")
                .replaceAll("\\s+", " ");
    }
}
