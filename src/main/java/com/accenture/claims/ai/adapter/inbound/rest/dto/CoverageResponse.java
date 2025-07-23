package com.accenture.claims.ai.adapter.inbound.rest.dto;

import lombok.Data;

@Data
public class CoverageResponse {
    private String response;
    private String coverageType;  // e.g., "TECHNICAL", "ADMIN"
    private String policyNumber;
}
