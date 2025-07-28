package com.accenture.claims.ai.adapter.outbound.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SpeechToTextServiceTest {

    @Inject
    SpeechToTextService speechToTextService;

    @Inject
    TestRestService testRestService;

    private Path tempDir;
    private Path audioFilePath;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory manually
        tempDir = Files.createTempDirectory("speech-to-text-test");
        
        // Create a mock audio file for testing
        audioFilePath = tempDir.resolve("test-audio.wav");
        // Write some dummy content to the file
        Files.write(audioFilePath, "dummy audio content".getBytes());
        
        // Manually set the TestRestService on the SpeechToTextService
        // This is necessary because the @Mock annotation doesn't seem to be working correctly
        java.lang.reflect.Field field;
        try {
            field = SpeechToTextService.class.getDeclaredField("restService");
            field.setAccessible(true);
            field.set(speechToTextService, testRestService);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    void testConvertAudioToText_Success_OldFormat() throws IOException {
        // Set the TestRestService to return a successful response in the old format
        String mockResponse = "{ \"status\": 200, \"body\": { \"text\": \"This is the transcribed text\" } }";
        testRestService.setResponseToReturn(mockResponse);

        // Call the method under test
        String result = speechToTextService.convertAudioToText(audioFilePath.toString());

        // Verify the result
        assertEquals("This is the transcribed text", result);
    }
    
    @Test
    void testConvertAudioToText_Success_NewFormat() throws IOException {
        // Set the TestRestService to return a successful response in the new format (2024-11-15)
        String mockResponse = "{ \"status\": 200, \"DisplayText\": \"This is the new format text\" }";
        testRestService.setResponseToReturn(mockResponse);

        // Call the method under test
        String result = speechToTextService.convertAudioToText(audioFilePath.toString());

        // Verify the result
        assertEquals("This is the new format text", result);
    }
    
    @Test
    void testConvertAudioToText_Success_NewFormat_Lowercase() throws IOException {
        // Set the TestRestService to return a successful response in the new format with lowercase field name
        String mockResponse = "{ \"status\": 200, \"displayText\": \"This is the lowercase variant\" }";
        testRestService.setResponseToReturn(mockResponse);

        // Call the method under test
        String result = speechToTextService.convertAudioToText(audioFilePath.toString());

        // Verify the result
        assertEquals("This is the lowercase variant", result);
    }

    @Test
    void testConvertAudioToText_ErrorResponse() {
        // Test the extractTranscribedText method directly
        String errorResponse = "{ \"status\": 500, \"error\": \"Internal server error\" }";
        
        // Call the method directly (now it's package-private)
        String result = speechToTextService.extractTranscribedText(errorResponse);
        
        // Verify the result
        assertEquals("Failed to transcribe audio", result);
    }

    @Test
    void testConvertAudioToText_MalformedResponse() {
        // Test the extractTranscribedText method directly
        String malformedResponse = "{ \"status\": 200, \"body\": \"malformed response\" }";
        
        // Call the method directly (now it's package-private)
        String result = speechToTextService.extractTranscribedText(malformedResponse);
        
        // Verify the result
        assertEquals("Failed to transcribe audio", result);
    }
}