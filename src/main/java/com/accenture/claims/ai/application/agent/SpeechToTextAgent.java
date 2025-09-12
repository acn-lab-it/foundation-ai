package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.adapter.outbound.rest.SpeechToTextService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;

/**
 * Agent for converting speech audio files to text
 * This agent is used by the FNOLAssistantAgent to process vocal messages
 *
 * It dynamically retrieves the language for the current session using SessionLanguageContext
 * and passes it to the SpeechToTextService to ensure transcription is done in the correct language.
 */
@ApplicationScoped
public class SpeechToTextAgent {

    @Inject
    SpeechToTextService speechToTextService;

    @Inject
    SessionLanguageContext sessionLanguageContext;
    /**
     * Tool LLM: converts audio to text using Azure Speech-to-Text API
     * @param sessionId id of the current session
     * @param audioFilePath absolute path to the audio file on the server filesystem
     * @return transcribed text from the audio file
     */
    @Tool("Convert audio to text. Parameters: sessionId, audioFilePath. Return transcribed text from the audio file.")
    public String transcribeAudio(String sessionId, String audioFilePath) {
        try {
            // Get the language for the current session
            String language = sessionLanguageContext.getLanguage(sessionId);

            // Convert audio to text using the service with the appropriate language
            return speechToTextService.convertAudioToText(audioFilePath, language);
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to transcribe audio: " + e.getMessage();
        }
    }
}
