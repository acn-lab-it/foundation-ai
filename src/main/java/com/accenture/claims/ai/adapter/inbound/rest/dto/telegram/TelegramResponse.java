package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents a response to be sent back to Telegram.
 * This is a simplified version of the Telegram sendMessage method parameters.
 * See https://core.telegram.org/bots/api#sendmessage for the full specification.
 */
public class TelegramResponse {
    public String method;
    public Long chat_id;
    public String text;

    // Default constructor for JSON serialization
    public TelegramResponse() {
    }

    public TelegramResponse(String method, Long chat_id, String text) {
        this.method = method;
        this.chat_id = chat_id;
        this.text = text;
    }

    /**
     * Creates a sendMessage response.
     *
     * @param chat_id The chat ID to send the message to
     * @param text The text of the message
     * @return A TelegramResponse configured for sending a message
     */
    public static TelegramResponse createSendMessageResponse(Long chat_id, String text) {
        return new TelegramResponse("sendMessage", chat_id, text);
    }
}