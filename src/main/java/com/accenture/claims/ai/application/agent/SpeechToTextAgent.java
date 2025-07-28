package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.adapter.outbound.rest.SpeechToTextService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;

/**
 * Agent for converting speech audio files to text
 * This agent is used by the FNOLAssistantAgent to process vocal messages
 */
@ApplicationScoped
public class SpeechToTextAgent {

    @Inject
    SpeechToTextService speechToTextService;

    /**
     * Tool LLM: converts audio to text using Azure Speech-to-Text API
     * @param sessionId id of the current session
     * @param audioFilePath absolute path to the audio file on the server filesystem
     * @return transcribed text from the audio file
     */
    @Tool("Convert audio to text. Parameters: sessionId, audioFilePath. Return transcribed text from the audio file.")
    public String transcribeAudio(String sessionId, String audioFilePath) {
        try {
            // Convert audio to text using the service
            return speechToTextService.convertAudioToText(audioFilePath);
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to transcribe audio: " + e.getMessage();
        }
    }
}