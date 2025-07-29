package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

import java.util.List;

/**
 * Represents a message from Telegram.
 * This is a simplified version of the Telegram Message object.
 * See https://core.telegram.org/bots/api#message for the full specification.
 */
public class TelegramMessage {
    public Long message_id;
    public TelegramUser from;
    public TelegramChat chat;
    public Long date;
    public String text;
    public List<TelegramPhotoSize> photo;
    public TelegramVoice voice;
    public TelegramDocument document;

    // Default constructor for JSON deserialization
    public TelegramMessage() {
    }

    public TelegramMessage(Long message_id, TelegramUser from, TelegramChat chat, Long date, String text) {
        this.message_id = message_id;
        this.from = from;
        this.chat = chat;
        this.date = date;
        this.text = text;
    }
}