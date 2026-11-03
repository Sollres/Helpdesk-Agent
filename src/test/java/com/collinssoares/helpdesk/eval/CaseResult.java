package com.collinssoares.helpdesk.eval;

import java.util.List;

/**
 * Résultat d'un cas d'évaluation.
 *
 * @param passed            le comportement attendu est respecté
 * @param failure           raison de l'échec, sinon null
 * @param sourceCorrect     la procédure attendue est citée (ANSWER_FROM_KB uniquement, sinon null)
 * @param categoryCorrect   la catégorie du ticket créé est la bonne (CREATE_TICKET uniquement, sinon null)
 * @param ticketCreatedWrongly un ticket a été créé alors que le cas ne le demandait pas
 * @param judgeScore        note de fidélité aux sources donnée par le LLM juge (1 à 5), null si non évalué
 * @param judgeReason       justification du juge
 * @param unsupported       affirmations jugées non fondées par le juge
 * @param latenciesMs       durée de chaque tour
 */
record CaseResult(EvalCase evalCase, boolean passed, String failure, Boolean sourceCorrect, Boolean categoryCorrect,
                  boolean ticketCreatedWrongly, String answer, List<String> sources, List<String> toolCalls,
                  String createdTicket, Integer judgeScore, String judgeReason, List<String> unsupported,
                  List<Long> latenciesMs) {

    /** Le juge a relevé une information inventée susceptible d'induire en erreur (note ≤ 3). */
    boolean unfaithful() {
        return judgeScore != null && judgeScore > 0 && judgeScore <= 3;
    }
}
