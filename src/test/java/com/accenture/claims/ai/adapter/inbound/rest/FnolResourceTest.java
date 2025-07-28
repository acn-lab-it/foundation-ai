package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.accenture.claims.ai.application.agent.TestFNOLAssistantAgent;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FnolResource
 * This test verifies that the FnolResource correctly handles audio messages
 */
@QuarkusTest
public class FnolResourceTest {
    
    @Inject
    FnolResource fnolResource;
    
    @Inject
    TestFNOLAssistantAgent testAgent;
    
    private Path tempDir;
    private Path audioFilePath;
    private FileUpload mockAudioFileUpload;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory manually
        tempDir = Files.createTempDirectory("fnol-resource-test");
        
        // Create a mock audio file for testing
        audioFilePath = tempDir.resolve("test-audio.wav");
        Files.write(audioFilePath, "dummy audio content".getBytes());
        
        // Create a mock FileUpload
        mockAudioFileUpload = new FileUpload() {
            @Override
            public String name() {
                return "userAudioMessage";
            }
            
            @Override
            public String fileName() {
                return "test-audio.wav";
            }
            
            @Override
            public Path uploadedFile() {
                return audioFilePath;
            }
            
            @Override
            public long size() {
                return 0;
            }
            
            @Override
            public String contentType() {
                return "audio/wav";
            }
            
            @Override
            public String charSet() {
                return null;
            }
            
            @Override
            public Path filePath() {
                return audioFilePath;
            }
        };
    }
    
    @AfterEach
    void tearDown() throws IOException {
        // Clean up the temporary directory
        if (tempDir != null && Files.exists(tempDir)) {
            Files.deleteIfExists(audioFilePath);
            Files.deleteIfExists(tempDir);
        }
    }
    
    @Test
    void testChat_WithAudioMessage() {
        // Create a ChatForm with an audio message
        ChatForm form = new ChatForm();
        form.userMessage = "Hello";
        form.userAudioMessage = mockAudioFileUpload;
        
        // Call the method under test
        Response response = fnolResource.chat(form, "en");
        
        // Verify the response status
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Verify that the audio message marker was added to the user message
        String userMessage = testAgent.getLastUserMessage();
        assertTrue(userMessage.contains("[AUDIO_MESSAGE]"));
        assertTrue(userMessage.contains("[/AUDIO_MESSAGE]"));
    }
}