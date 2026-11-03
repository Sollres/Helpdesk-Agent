package com.collinssoares.helpdesk.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.collinssoares.helpdesk.knowledge.KnowledgeBaseService;
import com.collinssoares.helpdesk.knowledge.KnowledgeSnippet;
import com.collinssoares.helpdesk.ticket.TicketService;

class ChatServiceTest {

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final TicketService ticketService = mock(TicketService.class);
    private final ChatService chatService = new ChatService(chatClient, knowledgeBaseService, ticketService);

    @BeforeEach
    void noTicketsByDefault() {
        when(ticketService.findSimilar(anyString())).thenReturn(List.of());
    }

    private void llmAnswers(String answer) {
        when(chatClient.prompt().user(anyString()).advisors(any(java.util.function.Consumer.class))
                .toolContext(any()).call().content())
                .thenReturn(answer);
    }

    @Test
    void returnsLlmAnswerAndKeepsConversationId() {
        when(knowledgeBaseService.search(anyString())).thenReturn(List.of());
        llmAnswers("Vérifiez que vous êtes connecté au réseau de l'entreprise.");

        ChatResponse response = chatService.chat(new ChatRequest("Imprimante introuvable", "conv-42"));

        assertThat(response.answer()).isEqualTo("Vérifiez que vous êtes connecté au réseau de l'entreprise.");
        assertThat(response.conversationId()).isEqualTo("conv-42");
        assertThat(response.ticket()).isNull();
    }

    @Test
    void generatesConversationIdWhenMissing() {
        when(knowledgeBaseService.search(anyString())).thenReturn(List.of());
        llmAnswers("Bonjour !");

        ChatResponse response = chatService.chat(new ChatRequest("Bonjour", null));

        assertThat(response.conversationId()).isNotBlank();
    }

    @Test
    void returnsOnlyVerifiedSources() {
        var vpn = new KnowledgeSnippet("Se connecter au VPN", "Escalade", "vpn.md", "Créez un ticket.", 0.8);
        when(knowledgeBaseService.search(anyString())).thenReturn(List.of(vpn));
        llmAnswers("Redémarrez.\n\nSource : Se connecter au VPN");

        ChatResponse response = chatService.chat(new ChatRequest("VPN en panne", null));

        assertThat(response.sources()).extracting("title").containsExactly("Se connecter au VPN");
    }
}
