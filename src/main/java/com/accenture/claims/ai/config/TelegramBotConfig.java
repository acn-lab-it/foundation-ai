package com.accenture.claims.ai.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Configuration class for Telegram Bot settings.
 * This class reads configuration values from application.properties.
 */
@ApplicationScoped
public class TelegramBotConfig {

    @ConfigProperty(name = "telegram.bot.token")
    String botToken;

    @ConfigProperty(name = "telegram.bot.username")
    String botUsername;

    @ConfigProperty(name = "telegram.bot.mode", defaultValue = "webhook")
    String botMode;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8085")
    int serverPort;

    @ConfigProperty(name = "telegram.webhook.path", defaultValue = "/telegram/webhook")
    String webhookPath;

    @ConfigProperty(name = "telegram.webhook.url")
    String webhookBaseUrl;

    @ConfigProperty(name = "telegram.bot.allowed-chat-ids", defaultValue = "")
    String allowedChatIdsString;

    private Set<Long> allowedChatIds;

    /**
     * Initializes the allowed chat IDs set by parsing the comma-separated list.
     * This method is called automatically when the bean is created.
     */
    @PostConstruct
    public void init() {
        if (allowedChatIdsString == null || allowedChatIdsString.trim().isEmpty()) {
            // If no chat IDs are specified, initialize as an empty set
            allowedChatIds = Collections.emptySet();
        } else {
            // Parse the comma-separated list of chat IDs
            allowedChatIds = Arrays.stream(allowedChatIdsString.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Gets the Telegram bot token.
     *
     * @return The bot token
     */
    public String getBotToken() {
        return botToken;
    }

    /**
     * Gets the server port.
     *
     * @return The server port
     */
    public int getServerPort() {
        return serverPort;
    }

    /**
     * Gets the webhook path.
     *
     * @return The webhook path
     */
    public String getWebhookPath() {
        return webhookPath;
    }

    /**
     * Gets the base URL for the webhook.
     *
     * @return The base URL
     */
    public String getWebhookBaseUrl() {
        return webhookBaseUrl;
    }

    /**
     * Gets the full webhook URL.
     *
     * @return The full webhook URL
     */
    public String getFullWebhookUrl() {
        return webhookBaseUrl + webhookPath;
    }

    /**
     * Gets the Telegram bot username.
     *
     * @return The bot username
     */
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Gets the Telegram bot mode (webhook or long_polling).
     *
     * @return The bot mode
     */
    public String getBotMode() {
        return botMode;
    }

    /**
     * Checks if the bot is in webhook mode.
     *
     * @return true if the bot is in webhook mode, false otherwise
     */
    public boolean isWebhookMode() {
        return "webhook".equalsIgnoreCase(botMode);
    }

    /**
     * Checks if the bot is in long polling mode.
     *
     * @return true if the bot is in long polling mode, false otherwise
     */
    public boolean isLongPollingMode() {
        return "long_polling".equalsIgnoreCase(botMode);
    }

    /**
     * Checks if a chat ID is allowed to use the bot.
     * If no allowed chat IDs are specified (empty set), all chat IDs are allowed.
     *
     * @param chatId The chat ID to check
     * @return true if the chat ID is allowed, false otherwise
     */
    public boolean isChatIdAllowed(Long chatId) {
        // If no chat IDs are specified (empty set), all chat IDs are allowed
        return allowedChatIds.isEmpty() || allowedChatIds.contains(chatId);
    }
}