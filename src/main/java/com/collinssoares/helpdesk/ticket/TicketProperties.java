package com.collinssoares.helpdesk.ticket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param topK                nombre maximal de tickets similaires renvoyés
 * @param similarityThreshold score de similarité minimal pour qu'un ticket soit jugé similaire
 */
@ConfigurationProperties("helpdesk.tickets")
public record TicketProperties(int topK, double similarityThreshold) {
}
