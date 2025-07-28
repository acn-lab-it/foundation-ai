package com.accenture.claims.ai.application.agent;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test implementation of FNOLAssistantAgent interface
 * This class is used for testing the FnolResource
 */
@Mock
@ApplicationScoped
public class TestFNOLAssistantAgent implements FNOLAssistantAgent {
    
    private String lastUserMessage;
    
    /**
     * Get the last user message that was passed to the chat method
     * @return The last user message
     */
    public String getLastUserMessage() {
        return lastUserMessage;
    }
    
    /**
     * Mock implementation of the chat method
     * This method stores the user message and returns a predefined response
     */
    @Override
    public String chat(String sessionId, String systemMessage, String userMessage) {
        this.lastUserMessage = userMessage;
        return "{\"answer\":\"Test response\"}";
    }
}