package com.collinssoares.helpdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.collinssoares.helpdesk.AbstractIntegrationTest;

/**
 * Tickets : création, suivi, recherche de tickets similaires et API REST, sur un vrai PostgreSQL + pgvector.
 */
@AutoConfigureMockMvc
class TicketIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TicketService ticketService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockMvc mockMvc;

    @Test
    void seededTicketsAreIndexedOnStartup() {
        Integer missing = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM tickets t WHERE NOT EXISTS (
                    SELECT 1 FROM vector_store v
                    WHERE v.metadata ->> 'type' = 'ticket' AND v.metadata ->> 'reference' = t.reference)""",
                Integer.class);

        assertThat(missing).isZero();
    }

    @Test
    void createsTicketWithReferenceAndMakesItSearchable() {
        TicketSummary created = ticketService.create("Clavier Bluetooth déconnecté",
                "Le clavier Bluetooth se déconnecte toutes les dix minutes.", TicketCategory.HARDWARE,
                TicketPriority.LOW, "test@entreprise.local");

        assertThat(created.reference()).matches("HD-" + Year.now().getValue() + "-\\d{6}");
        assertThat(created.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticketService.findSimilar("clavier Bluetooth qui se déconnecte"))
                .extracting(TicketSummary::reference)
                .first().isEqualTo(created.reference());
    }

    @Test
    void findsSimilarTicketWithItsResolution() {
        List<TicketSummary> similar = ticketService.findSimilar("second écran non détecté sur la station d'accueil");

        assertThat(similar).isNotEmpty();
        assertThat(similar.getFirst().title()).isEqualTo("Station d'accueil : second écran non détecté");
        assertThat(similar.getFirst().resolution()).contains("Dock Firmware Updater");
    }

    @Test
    void statusIncludesLastCommentAndIgnoresCase() {
        String reference = jdbcTemplate.queryForObject(
                "SELECT reference FROM tickets WHERE title = 'Copieur du 2e étage hors service'", String.class);

        TicketSummary status = ticketService.getStatus(" " + reference.toLowerCase() + " ");

        assertThat(status.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(status.lastComment()).contains("copieur du 3e étage");
    }

    @Test
    void unknownReferenceThrows() {
        assertThatThrownBy(() -> ticketService.getStatus("HD-1999-999999"))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("HD-1999-999999");
    }

    @Test
    void listsTicketsMostRecentFirstWithOptionalStatusFilter() throws Exception {
        List<TicketSummary> all = ticketService.list(null);
        assertThat(all).hasSizeGreaterThanOrEqualTo(10);
        assertThat(all).extracting(TicketSummary::updatedAt).isSortedAccordingTo(java.util.Comparator.reverseOrder());

        assertThat(ticketService.list(TicketStatus.IN_PROGRESS))
                .isNotEmpty()
                .allSatisfy(t -> assertThat(t.status()).isEqualTo(TicketStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/tickets").param("status", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESOLVED"));
    }

    @Test
    void restApiReturnsTicketDetailsOr404() throws Exception {
        String reference = jdbcTemplate.queryForObject(
                "SELECT reference FROM tickets WHERE title = 'Copieur du 2e étage hors service'", String.class);

        mockMvc.perform(get("/api/tickets/" + reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.reference").value(reference))
                .andExpect(jsonPath("$.comments[0].author").value("support"));
        mockMvc.perform(get("/api/tickets/HD-1999-999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Ticket introuvable"));
    }
}
