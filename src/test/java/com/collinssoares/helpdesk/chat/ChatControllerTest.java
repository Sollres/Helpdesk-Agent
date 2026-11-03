package com.collinssoares.helpdesk.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatService chatService;

    @Test
    void returnsAnswerFromService() throws Exception {
        given(chatService.chat(any())).willReturn(new ChatResponse("Redémarrez le client VPN.", "conv-1"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Mon VPN ne marche plus"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Redémarrez le client VPN."))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requête invalide"));
    }

    @Test
    void returnsBadGatewayWhenLlmFails() throws Exception {
        given(chatService.chat(any())).willThrow(new RestClientException("401 Unauthorized"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "Bonjour"}
                                """))
                .andExpect(status().isBadGateway());
    }
}
