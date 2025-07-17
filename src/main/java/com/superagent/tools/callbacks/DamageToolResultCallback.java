package com.superagent.tools.callbacks;

import dev.langchain4j.spi.Callback;

/**
 * Java port of damageToolResultCallback.js
 */
public class DamageToolResultCallback implements Callback {
    private String damageType = "";
    private String location = "";
    private double confidence = 0.0;

    @Override
    public void onToolEnd(String name, String content) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                            .readTree(content);
            this.damageType = node.path("damageType").asText("");
            this.location = node.path("location").asText("");
            this.confidence = node.path("confidence").asDouble(0);
        } catch (Exception e) {
            // ignore
        }
    }

    public String getDamageType() {
        return damageType;
    }

    public String getLocation() {
        return location;
    }

    public double getConfidence() {
        return confidence;
    }
}
