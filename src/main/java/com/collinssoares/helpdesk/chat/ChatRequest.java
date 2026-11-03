package com.collinssoares.helpdesk.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param message        message de l'utilisateur
 * @param conversationId identifiant de conversation renvoyé par la réponse précédente ; absent pour une nouvelle conversation
 * @param user           identifiant du salarié (en attendant un vrai SSO) ; utilisé comme demandeur des tickets
 */
public record ChatRequest(
        @NotBlank(message = "Le message ne peut pas être vide")
        @Size(max = 4000, message = "Le message ne doit pas dépasser 4000 caractères")
        String message,

        @Size(max = 64)
        String conversationId,

        @Size(max = 100)
        String user
) {

    public ChatRequest(String message, String conversationId) {
        this(message, conversationId, null);
    }
}
