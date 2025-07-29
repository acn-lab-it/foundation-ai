package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents an update from Telegram.
 * This is a simplified version of the Telegram Update object.
 * See https://core.telegram.org/bots/api#update for the full specification.
 */
public class TelegramUpdate {
    public Long update_id;
    public TelegramMessage message;
    
    // Default constructor for JSON deserialization
    public TelegramUpdate() {
    }
    
    public TelegramUpdate(Long update_id, TelegramMessage message) {
        this.update_id = update_id;
        this.message = message;
    }
}