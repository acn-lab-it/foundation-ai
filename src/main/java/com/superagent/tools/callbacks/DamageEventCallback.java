package com.superagent.tools.callbacks;

import dev.langchain4j.spi.Callback;

/**
 * Java port of damageEventCallback.js
 */
public class DamageEventCallback implements Callback {
    private String eventType = "";
    private double confidence = 0.0;

    @Override
    public void onToolEnd(String name, String content) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                            .readTree(content);
            this.eventType = node.path("eventType").asText("");
            this.confidence = node.path("confidence").asDouble(0);
        } catch (Exception e) {
            // ignore
        }
    }

    public String getEventType() {
        return eventType;
    }

    public double getConfidence() {
        return confidence;
    }
}
