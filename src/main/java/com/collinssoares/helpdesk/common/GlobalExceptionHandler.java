package com.collinssoares.helpdesk.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import com.collinssoares.helpdesk.ticket.TicketNotFoundException;

/**
 * Traduit les erreurs en réponses ProblemDetail (RFC 9457).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Requête invalide");
        return problem;
    }

    @ExceptionHandler(TicketNotFoundException.class)
    ProblemDetail handleTicketNotFound(TicketNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Ticket introuvable");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Requête invalide");
        return problem;
    }

    /** Erreur côté fournisseur LLM (clé invalide, quota, indisponibilité...). */
    @ExceptionHandler(RestClientException.class)
    ProblemDetail handleLlmError(RestClientException ex) {
        log.error("Erreur lors de l'appel au fournisseur LLM", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Le service d'IA est momentanément indisponible. Réessayez dans quelques instants.");
        problem.setTitle("Erreur du fournisseur LLM");
        return problem;
    }
}
