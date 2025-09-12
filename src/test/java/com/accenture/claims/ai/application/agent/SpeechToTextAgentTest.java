package com.accenture.claims.ai.application.agent;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SpeechToTextAgentTest {

    @Inject
    SpeechToTextAgent speechToTextAgent;

    @Inject
    TestSpeechToTextService testSpeechToTextService;
    
    @BeforeEach
    void setUp() {
        // Reset the TestSpeechToTextService before each test
        testSpeechToTextService.setThrowException(false, "");
        testSpeechToTextService.setTextToReturn("This is the transcribed text");
    }

    @Test
    void testTranscribeAudio_Success() {
        // Set the TestSpeechToTextService to return a successful response
        String expectedText = "This is the transcribed text";
        testSpeechToTextService.setTextToReturn(expectedText);

        // Call the method under test
        String result = speechToTextAgent.transcribeAudio("test-session-id", "/path/to/audio.wav");

        // Verify the result
        assertEquals(expectedText, result);
    }

    @Test
    void testTranscribeAudio_Exception() {
        // Set the TestSpeechToTextService to throw an exception
        String exceptionMessage = "Test exception";
        testSpeechToTextService.setThrowException(true, exceptionMessage);

        // Call the method under test
        String result = speechToTextAgent.transcribeAudio("test-session-id", "/path/to/audio.wav");

        // Verify the result contains the exception message
        assertTrue(result.contains("Failed to transcribe audio"));
        assertTrue(result.contains(exceptionMessage));
    }
}