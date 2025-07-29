package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents a chat in Telegram.
 * This is a simplified version of the Telegram Chat object.
 * See https://core.telegram.org/bots/api#chat for the full specification.
 */
public class TelegramChat {
    public Long id;
    public String type; // "private", "group", "supergroup" or "channel"
    public String title;
    public String username;
    public String first_name;
    public String last_name;

    // Default constructor for JSON deserialization
    public TelegramChat() {
    }

    public TelegramChat(Long id, String type, String title, String username, String first_name, String last_name) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.username = username;
        this.first_name = first_name;
        this.last_name = last_name;
    }
}