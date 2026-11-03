package com.collinssoares.helpdesk.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgentTraceTest {

    @ParameterizedTest
    @ValueSource(strings = {"Oui", "oui, crée le ticket", "OK vas-y", "D'accord", "Crée-moi un ticket", "yes"})
    void recognizesConfirmations(String message) {
        assertThat(new AgentTrace("u", message).userConfirmed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mon VPN ne marche pas", "Non merci", "Ça ne marche toujours pas", "Pourquoi ?"})
    void rejectsNonConfirmations(String message) {
        assertThat(new AgentTrace("u", message).userConfirmed()).isFalse();
    }

    @Test
    void limitsToolCallsPerMessage() {
        AgentTrace trace = new AgentTrace("u", "m");
        for (int i = 0; i < AgentTrace.MAX_TOOL_CALLS; i++) {
            trace.recordToolCall("tool");
        }

        assertThatThrownBy(() -> trace.recordToolCall("tool"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Limite");
    }
}
