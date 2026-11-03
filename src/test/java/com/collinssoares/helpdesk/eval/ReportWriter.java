package com.collinssoares.helpdesk.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Écrit le rapport d'évaluation en Markdown (eval/report.md).
 */
class ReportWriter {

    private ReportWriter() {
    }

    static String write(Path path, List<CaseResult> results, String chatModel, String embeddingModel)
            throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Rapport d'évaluation — HelpDesk Agent\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| **Date** | ").append(LocalDate.now()).append(" |\n");
        md.append("| **Modèle de chat** | `").append(chatModel).append("` |\n");
        md.append("| **Modèle d'embeddings** | `").append(embeddingModel).append("` |\n");
        md.append("| **Cas** | ").append(results.size()).append(" (").append(results.stream()
                .mapToInt(r -> r.evalCase().turns().size()).sum()).append(" messages) |\n");
        md.append("| **Commande** | `./mvnw verify -Peval` |\n\n");

        md.append("## Indicateurs\n\n");
        md.append("| Indicateur | Résultat | Cible (SPECS.md) |\n|---|---|---|\n");
        md.append(row("Comportement attendu respecté", ratio(results, r -> true, CaseResult::passed), "—"));
        md.append(row("Bonne procédure citée (questions couvertes par la base)",
                ratio(results, r -> r.sourceCorrect() != null, r -> Boolean.TRUE.equals(r.sourceCorrect())), "≥ 90 %"));
        long notCreate = results.stream().filter(r -> r.evalCase().type() != EvalCase.Type.CREATE_TICKET).count();
        long wrongTickets = results.stream().filter(CaseResult::ticketCreatedWrongly).count();
        md.append(row("Tickets créés à tort", wrongTickets + " / " + notCreate + " (" + pct(wrongTickets, notCreate) + ")", "≤ 10 %"));
        md.append(row("Bonne catégorie de ticket",
                ratio(results, r -> r.categoryCorrect() != null, r -> Boolean.TRUE.equals(r.categoryCorrect())), "—"));
        List<Integer> scores = results.stream().map(CaseResult::judgeScore).filter(s -> s != null && s > 0).toList();
        long judged = results.stream().filter(r -> r.judgeScore() != null).count();
        long unfaithful = results.stream().filter(CaseResult::unfaithful).count();
        md.append(row("Fidélité aux sources (LLM juge, note moyenne sur 5)",
                scores.isEmpty() ? "—" : "%.2f (%d verdicts lisibles sur %d)".formatted(
                        scores.stream().mapToInt(i -> i).average().orElse(0), scores.size(), judged),
                "—"));
        md.append(row("Réponses avec une information non fondée trompeuse (juge, note ≤ 3)", unfaithful + " / " + scores.size(), "0"));
        long[] latencies = results.stream().flatMap(r -> r.latenciesMs().stream()).mapToLong(Long::longValue).sorted().toArray();
        md.append(row("Temps de réponse p50 / p95", percentile(latencies, 50) / 1000.0 + " s / "
                + percentile(latencies, 95) / 1000.0 + " s", "p95 < 8 s"));
        md.append("\n");

        md.append("## Par comportement\n\n| Comportement | Réussite |\n|---|---|\n");
        for (EvalCase.Type type : EvalCase.Type.values()) {
            List<CaseResult> ofType = results.stream().filter(r -> r.evalCase().type() == type).toList();
            if (!ofType.isEmpty()) {
                md.append("| ").append(type.label).append(" (`").append(type).append("`) | ")
                        .append(ratio(ofType, r -> true, CaseResult::passed)).append(" |\n");
            }
        }
        md.append("\n");

        List<CaseResult> failures = results.stream().filter(r -> !r.passed() || r.unfaithful()).toList();
        md.append("## Échecs et réponses jugées peu fidèles\n\n");
        if (failures.isEmpty()) {
            md.append("Aucun.\n\n");
        }
        for (CaseResult r : failures) {
            md.append("### ").append(r.evalCase().id()).append(" — ").append(r.evalCase().type().label).append("\n\n");
            md.append("- **Messages** : ").append(r.evalCase().turns().stream().map(t -> "« " + t + " »").collect(Collectors.joining(" → "))).append("\n");
            if (!r.passed()) {
                md.append("- **Échec** : ").append(r.failure()).append("\n");
            }
            if (r.judgeScore() != null && r.judgeScore() > 0) {
                md.append("- **Juge** : ").append(r.judgeScore()).append("/5 — ").append(r.judgeReason()).append("\n");
                r.unsupported().forEach(u -> md.append("  - non fondé : ").append(u).append("\n"));
            }
            md.append("- **Outils** : ").append(r.toolCalls()).append(" — **Sources** : ").append(r.sources())
                    .append(" — **Ticket créé** : ").append(Objects.toString(r.createdTicket(), "aucun")).append("\n");
            md.append("- **Réponse** :\n\n").append(quote(r.answer())).append("\n\n");
        }

        md.append("## Détail des cas\n\n| Cas | Comportement | OK | Juge | Outils | Ticket | Durée |\n|---|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            md.append("| ").append(r.evalCase().id())
                    .append(" | ").append(r.evalCase().type())
                    .append(" | ").append(r.passed() ? "✅" : "❌")
                    .append(" | ").append(r.judgeScore() == null || r.judgeScore() == 0 ? "—" : r.judgeScore() + "/5")
                    .append(" | ").append(r.toolCalls().isEmpty() ? "—" : String.join(", ", r.toolCalls()))
                    .append(" | ").append(Objects.toString(r.createdTicket(), "—"))
                    .append(" | ").append(r.latenciesMs().stream().mapToLong(Long::longValue).sum() / 100 / 10.0).append(" s |\n");
        }

        md.append("\n## Méthode\n\n");
        md.append("- Chaque cas est joué dans une nouvelle conversation, sur une base PostgreSQL + pgvector neuve (Testcontainers) : "
                + "13 procédures et 10 tickets d'exemple, indexés avec le vrai modèle d'embeddings.\n");
        md.append("- Les vérifications de comportement sont **déterministes** : outils appelés, ticket créé ou non (et à quel tour), "
                + "sources vérifiées, référence de ticket présente dans la réponse.\n");
        md.append("- La fidélité aux sources est notée par un **LLM juge** (même modèle que l'agent) sur les cas à un seul tour : "
                + "il reçoit exactement le contexte fourni à l'agent et la réponse. C'est un signal indicatif, à confirmer par une relecture.\n");
        md.append("- Le jeu de données est dans [questions.jsonl](questions.jsonl). Les résultats varient légèrement d'une exécution à l'autre (LLM non déterministe).\n");

        String report = md.toString();
        Files.writeString(path, report);
        return report;
    }

    private static String row(String label, String value, String target) {
        return "| " + label + " | " + value + " | " + target + " |\n";
    }

    private static String ratio(List<CaseResult> results, Predicate<CaseResult> scope, Predicate<CaseResult> ok) {
        List<CaseResult> scoped = results.stream().filter(scope).toList();
        long passed = scoped.stream().filter(ok).count();
        return passed + " / " + scoped.size() + " (" + pct(passed, scoped.size()) + ")";
    }

    private static String pct(long part, long total) {
        return total == 0 ? "—" : Math.round(100.0 * part / total) + " %";
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static String quote(String text) {
        return Arrays.stream(text.split("\n")).map(l -> "> " + l).collect(Collectors.joining("\n"));
    }
}
