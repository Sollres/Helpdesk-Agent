package com.collinssoares.helpdesk.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.collinssoares.helpdesk.agent.AgentTrace;
import com.collinssoares.helpdesk.ticket.TicketCategory;
import com.collinssoares.helpdesk.ticket.TicketPriority;
import com.collinssoares.helpdesk.ticket.TicketService;
import com.collinssoares.helpdesk.ticket.TicketStatus;
import com.collinssoares.helpdesk.ticket.TicketSummary;

class TicketToolsTest {

    private final TicketService ticketService = mock(TicketService.class);
    private final TicketTools tools = new TicketTools(ticketService);

    private static final TicketSummary CREATED = new TicketSummary("HD-2026-000042", "VPN en échec",
            TicketCategory.NETWORK, TicketPriority.HIGH, TicketStatus.OPEN, null, null, null);

    private static ToolContext contextFor(AgentTrace trace) {
        return new ToolContext(Map.of(AgentTrace.TOOL_CONTEXT_KEY, trace));
    }

    @Test
    void createsTicketForRequesterOnceUserConfirmed() {
        when(ticketService.create(anyString(), anyString(), any(), any(), anyString())).thenReturn(CREATED);
        AgentTrace trace = new AgentTrace("lea@entreprise.local", "Oui, crée le ticket");

        TicketSummary ticket = tools.createTicket("VPN en échec", "Échec de la passerelle", TicketCategory.NETWORK,
                TicketPriority.HIGH, contextFor(trace));

        assertThat(ticket.reference()).isEqualTo("HD-2026-000042");
        assertThat(trace.createdTicket()).isEqualTo(CREATED);
        assertThat(trace.toolCalls()).containsExactly("createTicket");
        verify(ticketService).create(eq("VPN en échec"), eq("Échec de la passerelle"), eq(TicketCategory.NETWORK),
                eq(TicketPriority.HIGH), eq("lea@entreprise.local"));
    }

    @Test
    void refusesToCreateTicketWithoutUserConfirmation() {
        AgentTrace trace = new AgentTrace("lea@entreprise.local", "Ça ne marche toujours pas");

        assertThatThrownBy(() -> tools.createTicket("VPN", "Échec", TicketCategory.NETWORK, TicketPriority.HIGH,
                contextFor(trace)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accord");
        verify(ticketService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void refusesSecondTicketInSameMessage() {
        when(ticketService.create(anyString(), anyString(), any(), any(), anyString())).thenReturn(CREATED);
        AgentTrace trace = new AgentTrace("lea@entreprise.local", "oui");
        tools.createTicket("VPN", "Échec", TicketCategory.NETWORK, TicketPriority.HIGH, contextFor(trace));

        assertThatThrownBy(() -> tools.createTicket("VPN bis", "Échec", TicketCategory.NETWORK, TicketPriority.HIGH,
                contextFor(trace)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HD-2026-000042");
    }

    @Test
    void getTicketStatusDelegatesToService() {
        when(ticketService.getStatus("HD-2026-000002")).thenReturn(CREATED);
        AgentTrace trace = new AgentTrace("u", "où en est HD-2026-000002 ?");

        assertThat(tools.getTicketStatus("HD-2026-000002", contextFor(trace))).isEqualTo(CREATED);
        assertThat(trace.toolCalls()).containsExactly("getTicketStatus");
    }
}
