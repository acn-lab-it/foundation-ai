package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.adapter.outbound.rest.SpeechToTextService;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;

/**
 * Test implementation of SpeechToTextService that returns predefined responses
 * This class is used for testing the SpeechToTextAgent
 */
@Mock
@ApplicationScoped
public class TestSpeechToTextService extends SpeechToTextService {
    
    private String textToReturn = "This is the transcribed text";
    private boolean throwException = false;
    private String exceptionMessage = "Test exception";
    
    /**
     * Set the text that will be returned by the convertAudioToText method
     * @param text The text to return
     */
    public void setTextToReturn(String text) {
        this.textToReturn = text;
    }
    
    /**
     * Configure the service to throw an exception when convertAudioToText is called
     * @param throwException Whether to throw an exception
     * @param exceptionMessage The exception message
     */
    public void setThrowException(boolean throwException, String exceptionMessage) {
        this.throwException = throwException;
        this.exceptionMessage = exceptionMessage;
    }
    
    /**
     * Override the convertAudioToText method to return a predefined response or throw an exception
     */
    @Override
    public String convertAudioToText(String audioFilePath, String language) throws IOException {
        if (throwException) {
            throw new IOException(exceptionMessage);
        }
        return textToReturn;
    }
}