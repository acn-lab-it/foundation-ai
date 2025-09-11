package com.accenture.claims.ai.adapter.inbound.rest.dto.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailDto {
    private Map<String, AttachmentDto> attachments;
    private String subject;
    private String emailId;
    private String from;
    private String html;
    private Integer attachmentCount;
    private String to;
    private String text;
    private LocalDateTime timestamp;
}

