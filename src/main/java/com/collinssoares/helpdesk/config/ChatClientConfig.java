package com.collinssoares.helpdesk.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import com.collinssoares.helpdesk.agent.KnowledgeContextAdvisor;
import com.collinssoares.helpdesk.tools.KnowledgeBaseTools;
import com.collinssoares.helpdesk.tools.TicketTools;

@Configuration
@ConfigurationPropertiesScan("com.collinssoares.helpdesk")
public class ChatClientConfig {

    /**
     * ChatClient de l'agent : prompt système, mémoire de conversation, injection des procédures et outils.
     * Le Builder est auto-configuré par Spring AI à partir du fournisseur actif (Ollama ou OpenAI).
     *
     * <p>Ordre des advisors : mémoire (+200) → procédures (+250) → boucle d'appel d'outils (+300).
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder,
                          @Value("classpath:prompts/system.st") Resource systemPrompt,
                          ChatMemory chatMemory,
                          KnowledgeContextAdvisor knowledgeContextAdvisor,
                          KnowledgeBaseTools knowledgeBaseTools,
                          TicketTools ticketTools) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), knowledgeContextAdvisor)
                .defaultTools(knowledgeBaseTools, ticketTools)
                .build();
    }
}
