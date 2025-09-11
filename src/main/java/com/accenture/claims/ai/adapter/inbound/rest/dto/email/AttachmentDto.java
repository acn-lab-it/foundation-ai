package com.accenture.claims.ai.adapter.inbound.rest.dto.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentDto {
    private String filename;
    private long size;
    private String contentType;
}
