package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents a general file (as opposed to photos, voice messages and audio files).
 * This is a simplified version of the Telegram Document object.
 * See https://core.telegram.org/bots/api#document for the full specification.
 */
public class TelegramDocument {
    public String file_id;
    public String file_unique_id;
    public TelegramPhotoSize thumb;
    public String file_name;
    public String mime_type;
    public Integer file_size;
    
    // Default constructor for JSON deserialization
    public TelegramDocument() {
    }
    
    public TelegramDocument(String file_id, String file_unique_id, TelegramPhotoSize thumb, 
                           String file_name, String mime_type, Integer file_size) {
        this.file_id = file_id;
        this.file_unique_id = file_unique_id;
        this.thumb = thumb;
        this.file_name = file_name;
        this.mime_type = mime_type;
        this.file_size = file_size;
    }
}