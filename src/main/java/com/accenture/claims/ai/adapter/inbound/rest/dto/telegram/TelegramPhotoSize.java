package com.accenture.claims.ai.adapter.inbound.rest.dto.telegram;

/**
 * Represents one size of a photo or a file / sticker thumbnail.
 * This is a simplified version of the Telegram PhotoSize object.
 * See https://core.telegram.org/bots/api#photosize for the full specification.
 */
public class TelegramPhotoSize {
    public String file_id;
    public String file_unique_id;
    public Integer width;
    public Integer height;
    public Integer file_size;
    
    // Default constructor for JSON deserialization
    public TelegramPhotoSize() {
    }
    
    public TelegramPhotoSize(String file_id, String file_unique_id, Integer width, Integer height, Integer file_size) {
        this.file_id = file_id;
        this.file_unique_id = file_unique_id;
        this.width = width;
        this.height = height;
        this.file_size = file_size;
    }
}