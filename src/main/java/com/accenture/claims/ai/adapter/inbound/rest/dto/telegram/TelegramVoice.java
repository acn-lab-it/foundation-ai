package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents a voice note.
 * This is a simplified version of the Telegram Voice object.
 * See https://core.telegram.org/bots/api#voice for the full specification.
 */
public class TelegramVoice {
    public String file_id;
    public String file_unique_id;
    public Integer duration;
    public String mime_type;
    public Integer file_size;
    
    // Default constructor for JSON deserialization
    public TelegramVoice() {
    }
    
    public TelegramVoice(String file_id, String file_unique_id, Integer duration, String mime_type, Integer file_size) {
        this.file_id = file_id;
        this.file_unique_id = file_unique_id;
        this.duration = duration;
        this.mime_type = mime_type;
        this.file_size = file_size;
    }
}