package com.collinssoares.helpdesk.knowledge;

/**
 * @param source nom du fichier
 * @param chunks nombre de passages indexés (0 si le document n'a pas changé)
 * @param status INDEXED si le document a été (ré)indexé, UNCHANGED s'il était déjà à jour
 */
public record IngestionResult(String source, int chunks, Status status) {

    public enum Status { INDEXED, UNCHANGED }
}
