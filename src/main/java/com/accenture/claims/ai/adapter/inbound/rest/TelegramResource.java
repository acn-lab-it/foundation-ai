package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.dto.telegram.TelegramResponse;
import com.accenture.claims.ai.adapter.inbound.rest.dto.telegram.TelegramUpdate;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.config.TelegramBotConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resource for handling Telegram webhook requests.
 * This endpoint integrates with the FNOLAssistantAgent to process Telegram messages.
 */
@jakarta.ws.rs.Path("/telegram")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TelegramResource {

    @Inject
    FNOLAssistantAgent agent;

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    TelegramBotConfig telegramBotConfig;

    @Inject
    LanguageHelper languageHelper;

    @Inject
    GuardrailsContext guardrailsContext;

    /**
     * Handles incoming webhook requests from Telegram.
     *
     * @param update The Telegram update object
     * @return A response to be sent back to Telegram
     */
    @POST
    @jakarta.ws.rs.Path("/webhook")
    public Response handleWebhook(TelegramUpdate update) {
        if (update == null || update.message == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid update format\"}").build();
        }

        // Extract chat ID for response
        Long chatId = update.message.chat.id;

        // Check if the chat ID is allowed
        if (!telegramBotConfig.isChatIdAllowed(chatId)) {
            // Return access denied response
            TelegramResponse accessDeniedResponse = TelegramResponse.createSendMessageResponse(
                chatId, "Access denied. You are not authorized to use this bot.");
            return Response.ok(accessDeniedResponse).build();
        }

        // Generate or retrieve session ID
        // We use chat ID as part of the session ID to maintain conversation context per chat
        String sessionId = "telegram-" + chatId;

        // Extract user message
        String userMessage = update.message.text;

        // Handle voice messages
        if (update.message.voice != null) {
            try {
                // Download voice file and save to temp directory
                // Note: In a real implementation, you would use Telegram's getFile API to download the file
                // For this example, we'll just create a placeholder
                Path tmpDir = Files.createTempDirectory("telegram-audio-");
                Path audioFile = tmpDir.resolve("voice-" + update.message.voice.file_id + ".ogg");

                // Format as audio message for the agent
                userMessage += "[AUDIO_MESSAGE]\n" + audioFile.toString() + "\n[/AUDIO_MESSAGE]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"audio_processing_failure\"}")
                        .build();
            }
        }

        // Handle photo messages
        if (update.message.photo != null && !update.message.photo.isEmpty()) {
            try {
                // Download photo file and save to temp directory
                // Note: In a real implementation, you would use Telegram's getFile API to download the file
                // For this example, we'll just create a placeholder
                Path tmpDir = Files.createTempDirectory("telegram-media-");
                Path mediaFile = tmpDir.resolve("photo-" + update.message.photo.get(0).file_id + ".jpg");

                // Format as media file for the agent
                userMessage += (userMessage != null ? userMessage : "") +
                        "\n\n[MEDIA_FILES]\n" + mediaFile.toString() + "\n[/MEDIA_FILES]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"media_processing_failure\"}")
                        .build();
            }
        }

        // Handle document messages
        if (update.message.document != null) {
            try {
                // Download document file and save to temp directory
                // Note: In a real implementation, you would use Telegram's getFile API to download the file
                // For this example, we'll just create a placeholder
                Path tmpDir = Files.createTempDirectory("telegram-media-");
                Path mediaFile = tmpDir.resolve(update.message.document.file_name);

                // Format as media file for the agent
                userMessage = (userMessage != null ? userMessage : "") +
                              "\n\n[MEDIA_FILES]\n" + mediaFile.toString() + "\n[/MEDIA_FILES]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"document_processing_failure\"}")
                        .build();
            }
        }

        // If no message content was extracted, return an error
        if (userMessage == null || userMessage.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"No message content found\"}").build();
        }

        // Get language from user if available, otherwise use default
        String language = update.message.from != null && update.message.from.language_code != null
                ? update.message.from.language_code
                : "en";

        // Get the appropriate prompt for the language
        LanguageHelper.PromptResult promptResult = languageHelper.getPromptWithLanguage(language, "superAgent.mainPrompt");

        // Inject the sessionId into the prompt
        String systemPrompt = promptResult.prompt.replace("{{sessionId}}", sessionId);

        // Set the language for the session
        sessionLanguageContext.setLanguage(sessionId, promptResult.language);

        // Process the message with the agent
        String raw = agent.chat(sessionId, systemPrompt, userMessage);

        // Parse the response
        String answer;
        try {
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(raw);
            answer = node.has("answer") ? node.get("answer").asText() : raw;
        } catch (Exception ex) {
            // If the model doesn't follow the schema, fallback to raw response
            answer = raw;
        }

        // Create response for Telegram
        TelegramResponse telegramResponse = TelegramResponse.createSendMessageResponse(chatId, answer);

        return Response.ok(telegramResponse).build();
    }
}