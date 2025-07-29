package com.accenture.claims.ai.config;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service for initializing and managing Telegram bots.
 * This service handles both webhook and long polling approaches based on configuration.
 */
@ApplicationScoped
public class TelegramBotService {

    private static final Logger LOG = Logger.getLogger(TelegramBotService.class);
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private static final int TIMEOUT_SECONDS = 10;
    
    @Inject
    TelegramBotConfig config;
    
    @Inject
    TelegramBotLongPolling longPollingBot;
    
    private TelegramBotsApi botsApi;
    
    /**
     * Initializes the appropriate Telegram bot when the application starts.
     * This method is called automatically by Quarkus when the application starts.
     *
     * @param event The startup event
     */
    void onStart(@Observes StartupEvent event) {
        LOG.info("Initializing Telegram bot in mode: " + config.getBotMode());
        
        if (config.isWebhookMode()) {
            initializeWebhook();
        } else if (config.isLongPollingMode()) {
            initializeLongPolling();
        } else {
            LOG.warn("Unknown bot mode: " + config.getBotMode() + ". Bot will not be initialized.");
        }
    }
    
    /**
     * Cleans up resources when the application shuts down.
     * This method is called automatically by Quarkus when the application shuts down.
     *
     * @param event The shutdown event
     */
    void onShutdown(@Observes ShutdownEvent event) {
        LOG.info("Shutting down Telegram bot");
        
        if (config.isWebhookMode()) {
            // Nothing to do for webhook mode
        } else if (config.isLongPollingMode() && botsApi != null) {
            // Nothing specific to do for long polling mode
            // The bot session will be closed automatically
        }
    }
    
    /**
     * Initializes the webhook approach.
     * This registers the webhook URL with Telegram's API.
     */
    private void initializeWebhook() {
        String botToken = config.getBotToken();
        String webhookUrl = config.getFullWebhookUrl();
        
        LOG.info("Registering webhook URL: " + webhookUrl);
        
        // Build the URL for setting the webhook
        String setWebhookUrl = TELEGRAM_API_URL + botToken + "/setWebhook?url=" + webhookUrl;
        
        try {
            // Create HTTP client
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(setWebhookUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
            
            // Send request and get response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check response
            if (response.statusCode() == 200) {
                LOG.info("Webhook registered successfully: " + response.body());
            } else {
                LOG.error("Failed to register webhook. Status code: " + response.statusCode());
                LOG.error("Response body: " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Error registering webhook", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Initializes the long polling approach.
     * This registers the long polling bot with the Telegram API.
     */
    private void initializeLongPolling() {
        try {
            LOG.info("Initializing long polling bot with username: " + config.getBotUsername());
            
            // Create the TelegramBotsApi instance
            botsApi = new TelegramBotsApi(DefaultBotSession.class);
            
            // Register the bot
            botsApi.registerBot(longPollingBot);
            
            LOG.info("Long polling bot registered successfully");
        } catch (TelegramApiException e) {
            LOG.error("Error initializing long polling bot", e);
        }
    }
    
    /**
     * Gets information about the webhook from Telegram's API.
     * This can be used for debugging purposes.
     *
     * @return The webhook information as a string
     */
    public String getWebhookInfo() {
        String botToken = config.getBotToken();
        String getWebhookInfoUrl = TELEGRAM_API_URL + botToken + "/getWebhookInfo";
        
        try {
            // Create HTTP client
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
            
            // Create HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getWebhookInfoUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .build();
            
            // Send request and get response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Return response body
            return response.body();
        } catch (IOException | InterruptedException e) {
            LOG.error("Error getting webhook info", e);
            Thread.currentThread().interrupt();
            return "Error: " + e.getMessage();
        }
    }
}