package com.collinssoares.helpdesk.knowledge;

/**
 * Passage de la base de connaissances renvoyé par une recherche.
 *
 * @param title   titre de la procédure
 * @param section intitulé de la section
 * @param source  nom du fichier d'origine
 * @param content texte du passage
 * @param score   similarité avec la requête (0 à 1)
 */
public record KnowledgeSnippet(String title, String section, String source, String content, Double score) {
}
