package com.accenture.claims.ai.adapter.inbound.rest.dto.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadedAttachment {
    private byte[] content;
    private String contentType;
    private String contentDisposition;
}