package com.accenture.claims.ai.config;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implementation of a Telegram bot using the long polling mechanism.
 * This is primarily used for local development and testing.
 */
@ApplicationScoped
public class TelegramBotLongPolling extends TelegramLongPollingBot {

    private static final Logger LOG = Logger.getLogger(TelegramBotLongPolling.class);
    
    private final TelegramBotConfig config;
    private final FNOLAssistantAgent agent;
    private final SessionLanguageContext sessionLanguageContext;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Inject
    public TelegramBotLongPolling(TelegramBotConfig config, 
                                 FNOLAssistantAgent agent, 
                                 SessionLanguageContext sessionLanguageContext) {
        super(config.getBotToken());
        this.config = config;
        this.agent = agent;
        this.sessionLanguageContext = sessionLanguageContext;
        LOG.info("TelegramBotLongPolling initialized with username: " + config.getBotUsername());
    }
    
    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        LOG.info("Received update: " + update.getUpdateId());
        
        if (!update.hasMessage()) {
            LOG.warn("Update doesn't contain a message");
            return;
        }
        
        
        
        Message message = update.getMessage();
        Long chatId = message.getChatId();
        
        // Check if the chat ID is allowed
        if (!config.isChatIdAllowed(chatId)) {
            // Send access denied message
            sendErrorMessage(chatId, "Access denied. You are not authorized to use this bot.");
            return;
        }
        
        // Generate session ID based on chat ID
        String sessionId = "telegram-" + chatId;
        
        // Extract user message
        String userMessage = message.getText();
        
        // Handle voice messages
        if (message.hasVoice()) {
            try {
                // In a real implementation, you would download the voice file
                // For this example, we'll just create a placeholder
                Path tmpDir = Files.createTempDirectory("telegram-audio-");
                Path audioFile = tmpDir.resolve("voice-" + message.getVoice().getFileId() + ".ogg");
                
                // Format as audio message for the agent
                userMessage = "[AUDIO_MESSAGE]\n" + audioFile.toString() + "\n[/AUDIO_MESSAGE]";
                LOG.info("Processing voice message: " + audioFile);
            } catch (IOException e) {
                LOG.error("Error processing voice message", e);
                sendErrorMessage(chatId, "Sorry, I couldn't process your voice message.");
                return;
            }
        }
        
        // Handle photo messages
        if (message.hasPhoto() && !message.getPhoto().isEmpty()) {
            try {
                // In a real implementation, you would download the photo file
                // For this example, we'll just create a placeholder
                Path tmpDir = Files.createTempDirectory("telegram-media-");
                Path mediaFile = tmpDir.resolve("photo-" + message.getPhoto().get(0).getFileId() + ".jpg");
                
                // Format as media file for the agent
                userMessage = (userMessage != null ? userMessage : "") + 
                              "\n\n[MEDIA_FILES]\n" + mediaFile.toString() + "\n[/MEDIA_FILES]";
                LOG.info("Processing photo: " + mediaFile);
            } catch (IOException e) {
                LOG.error("Error processing photo", e);
                sendErrorMessage(chatId, "Sorry, I couldn't process your photo.");
                return;
            }
        }
        
        // Handle document messages
        if (message.hasDocument()) {
            try {
                // In a real implementation, you would download the document file
                // For this example, we'll just create a placeholder
                Path tmpDir = Files.createTempDirectory("telegram-media-");
                Path mediaFile = tmpDir.resolve(message.getDocument().getFileName());
                
                // Format as media file for the agent
                userMessage = (userMessage != null ? userMessage : "") + 
                              "\n\n[MEDIA_FILES]\n" + mediaFile.toString() + "\n[/MEDIA_FILES]";
                LOG.info("Processing document: " + mediaFile);
            } catch (IOException e) {
                LOG.error("Error processing document", e);
                sendErrorMessage(chatId, "Sorry, I couldn't process your document.");
                return;
            }
        }
        
        // If no message content was extracted, return an error
        if (userMessage == null || userMessage.isBlank()) {
            sendErrorMessage(chatId, "Sorry, I couldn't understand your message.");
            return;
        }
        
        // Get language from user if available, otherwise use default
        String language = message.getFrom().getLanguageCode() != null 
                ? message.getFrom().getLanguageCode() 
                : "en";
        
        try {
            // Get the appropriate prompt for the language
            LanguageHelper.PromptResult promptResult = LanguageHelper.getPromptWithLanguage(language, "superAgent.mainPrompt");
            
            // Inject the sessionId into the prompt
            String systemPrompt = promptResult.prompt.replace("{{sessionId}}", sessionId);
            
            // Set the language for the session
            sessionLanguageContext.setLanguage(sessionId, promptResult.language);
            
            // Process the message with the agent
            LOG.info("Processing message with agent: " + userMessage);
            String raw = agent.chat(sessionId, systemPrompt, userMessage);
            
            // Parse the response
            String answer;
            try {
                var node = objectMapper.readTree(raw);
                answer = node.has("answer") ? node.get("answer").asText() : raw;
            } catch (Exception ex) {
                // If the model doesn't follow the schema, fallback to raw response
                LOG.warn("Failed to parse agent response as JSON, using raw response", ex);
                answer = raw;
            }
            
            // Send response back to user
            sendResponse(chatId, answer);
            
        } catch (Exception e) {
            LOG.error("Error processing message", e);
            sendErrorMessage(chatId, "Sorry, an error occurred while processing your message.");
        }
    }
    
    /**
     * Sends a response message to the user.
     * 
     * @param chatId The chat ID to send the message to
     * @param text The text of the message
     */
    private void sendResponse(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        
        try {
            execute(message);
            LOG.info("Response sent to chat " + chatId);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send response", e);
        }
    }
    
    /**
     * Sends an error message to the user.
     * 
     * @param chatId The chat ID to send the message to
     * @param errorMessage The error message
     */
    private void sendErrorMessage(Long chatId, String errorMessage) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(errorMessage);
        
        try {
            execute(message);
            LOG.info("Error message sent to chat " + chatId);
        } catch (TelegramApiException e) {
            LOG.error("Failed to send error message", e);
        }
    }
}