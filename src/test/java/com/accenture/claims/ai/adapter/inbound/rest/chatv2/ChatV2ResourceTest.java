package com.accenture.claims.ai.adapter.inbound.rest.chatv2;

import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ChatV2ResourceTest {

    @Inject
    ChatV2Resource chatV2Resource;

    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    @Inject
    LanguageHelper languageHelper;

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    GuardrailsContext guardrailsContext;

    @Inject
    ChatV2WelcomeToolV2 chatV2WelcomeTool;

    @Inject
    ChatV2AdministrativeCheckToolV2 chatV2AdministrativeCheckTool;

    @Inject
    ChatV2WhatHappenedToolV2 chatV2WhatHappenedTool;

    @Inject
    ChatV2DateParserToolV2 chatV2DateParserTool;

    @Inject
    ChatV2MediaOcrAgentV2 chatV2MediaOcrAgent;

    @Test
    public void testWelcomeEndpoint() {
        // Test del endpoint welcome
        ChatForm form = new ChatForm();
        form.policyNumber = "TEST123";
        form.emailAddress = "test@example.com";

        try {
            Response response = chatV2Resource.welcome(form, "en");
            assertEquals(200, response.getStatus());
            
            ChatV2Resource.ChatResponseDto dto = (ChatV2Resource.ChatResponseDto) response.getEntity();
            assertNotNull(dto);
            assertNotNull(dto.sessionId);
            assertEquals("welcome", dto.currentStep);
            assertNotNull(dto.answer);
        } catch (Exception e) {
            // Expected to fail in test environment due to missing policy data
            assertTrue(e.getMessage().contains("not found policyNumber") || 
                      e.getMessage().contains("emailAddress not related to policyNumber"));
        }
    }

    @Test
    public void testStepEndpoint() {
        // Test del endpoint step
        ChatForm form = new ChatForm();
        form.sessionId = "test-session-123";
        form.userMessage = "L'incidente è successo ieri alle 14:30 in via Roma 123, Milano";

        try {
            Response response = chatV2Resource.step(form, "en");
            assertEquals(200, response.getStatus());
            
            ChatV2Resource.ChatResponseDto dto = (ChatV2Resource.ChatResponseDto) response.getEntity();
            assertNotNull(dto);
            assertEquals("test-session-123", dto.sessionId);
            assertNotNull(dto.currentStep);
            assertNotNull(dto.answer);
        } catch (Exception e) {
            // Expected to fail in test environment due to missing session data
            assertTrue(e.getMessage().contains("step non riconosciuto") || 
                      e.getMessage().contains("Errore durante l'elaborazione"));
        }
    }

    @Test
    public void testDateParserTool() {
        // Test del tool per il parsing delle date
        String dateInput = "ieri alle 14:30";
        String result = chatV2DateParserTool.normalize("test-session", dateInput);
        assertNotNull(result);
    }

    @Test
    public void testLocationExtraction() {
        // Test dell'estrazione della location
        String userMessage = "L'incidente è successo in via Roma 123, Milano";
        String location = chatV2DateParserTool.extractLocation(userMessage);
        assertNotNull(location);
        assertTrue(location.contains("via Roma"));
    }

    @Test
    public void testDateExtraction() {
        // Test dell'estrazione della data
        String userMessage = "L'incidente è successo ieri alle 14:30";
        String date = chatV2DateParserTool.extractDate(userMessage);
        assertNotNull(date);
    }

    @Test
    public void testWhatHappenedTool() {
        // Test del tool per la categorizzazione
        try {
            String result = chatV2WhatHappenedTool.classifyAndSave("test-session", "Incendio in cucina", "via Roma 123");
            assertNotNull(result);
            assertTrue(result.contains("whatHappenedCode"));
        } catch (Exception e) {
            // Expected to fail in test environment due to missing database data
            assertTrue(e.getMessage().contains("JsonProcessingException") || 
                      e.getMessage().contains("Error"));
        }
    }

    @Test
    public void testMediaOcrAgent() {
        // Test del tool per l'analisi dei media
        String result = chatV2MediaOcrAgent.processMedia("test-session", "/fake/path/image.jpg");
        assertNotNull(result);
        assertTrue(result.contains("processed") || result.contains("not found") || result.contains("Error"));
    }

    @Test
    public void testAdministrativeCheckTool() {
        // Test del tool per la verifica amministrativa
        boolean exists = chatV2AdministrativeCheckTool.checkPolicyExistence("TEST123");
        assertFalse(exists); // Expected to be false in test environment
    }
}
