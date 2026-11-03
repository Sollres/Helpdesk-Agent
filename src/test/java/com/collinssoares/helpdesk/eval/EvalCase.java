package com.collinssoares.helpdesk.eval;

import java.util.List;

/**
 * Un cas du jeu d'évaluation (eval/questions.jsonl).
 *
 * @param id                  identifiant du cas
 * @param type                comportement attendu de l'agent
 * @param turns               messages successifs de l'utilisateur ; « {ref:Titre} » est remplacé par la référence du ticket
 * @param expectedSource      procédure qui doit être citée (ANSWER_FROM_KB)
 * @param expectedTicketTitle ticket existant qui doit être mentionné (KNOWN_INCIDENT, KNOWN_SOLUTION, GET_STATUS)
 * @param expectedCategory    catégorie attendue du ticket créé (CREATE_TICKET)
 * @param expectedKeywords    au moins un de ces mots doit figurer dans la réponse (GET_STATUS)
 */
record EvalCase(String id, Type type, List<String> turns, String expectedSource, String expectedTicketTitle,
                String expectedCategory, List<String> expectedKeywords) {

    enum Type {
        ANSWER_FROM_KB("Répond avec la bonne procédure"),
        KNOWN_INCIDENT("Signale l'incident en cours"),
        KNOWN_SOLUTION("Propose la solution d'un ticket résolu"),
        PROPOSE_TICKET("Propose un ticket sans le créer"),
        CREATE_TICKET("Crée le ticket après accord"),
        GET_STATUS("Donne le statut d'un ticket"),
        UNKNOWN_TICKET("Signale une référence inconnue"),
        OUT_OF_SCOPE("Refuse le hors sujet"),
        NO_ACTION("Résiste à l'injection de prompt");

        final String label;

        Type(String label) {
            this.label = label;
        }
    }
}
