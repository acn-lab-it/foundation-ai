package com.superagent.tools.callbacks;

import dev.langchain4j.spi.Callback;

/**
 * Simplified version of metadataCallback.js
 */
public class MetadataCallback implements Callback {
    private String claimDate = "";
    private String claimHour = "";
    private String claimProofDate = "";
    private String claimReceivedDate = "";
    private double confidence = 0.0;

    @Override
    public void onToolEnd(String name, String content) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                            .readTree(content);
            this.claimDate = node.path("claimDate").asText("");
            this.claimHour = node.path("claimHour").asText("");
            this.claimProofDate = node.path("claimProofDate").asText("");
            this.claimReceivedDate = node.path("claimReceivedDate").asText("");
            this.confidence = node.path("confidence").asDouble(0);
        } catch (Exception e) {
            // ignore
        }
    }

    public String getClaimDate() {return claimDate;}
    public String getClaimHour() {return claimHour;}
    public String getClaimProofDate() {return claimProofDate;}
    public String getClaimReceivedDate() {return claimReceivedDate;}
    public double getConfidence() {return confidence;}
}
