package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents a Telegram user or bot.
 * This is a simplified version of the Telegram User object.
 * See https://core.telegram.org/bots/api#user for the full specification.
 */
public class TelegramUser {
    public Long id;
    public Boolean is_bot;
    public String first_name;
    public String last_name;
    public String username;
    public String language_code;
    
    // Default constructor for JSON deserialization
    public TelegramUser() {
    }
    
    public TelegramUser(Long id, Boolean is_bot, String first_name, String last_name, String username, String language_code) {
        this.id = id;
        this.is_bot = is_bot;
        this.first_name = first_name;
        this.last_name = last_name;
        this.username = username;
        this.language_code = language_code;
    }
}