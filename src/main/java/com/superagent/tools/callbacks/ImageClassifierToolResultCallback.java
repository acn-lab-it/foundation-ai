package com.superagent.tools.callbacks;

import dev.langchain4j.spi.Callback;

/**
 * Equivalent of toolMessageCallback.js in Java.
 */
public class ImageClassifierToolResultCallback implements Callback {

    private String damageCategory = "";
    private String damagedEntity = "";
    private double confidence = 0.0;

    @Override
    public void onToolEnd(String name, String content) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                            .readTree(content);
            this.damageCategory = node.path("damageCategory").asText("");
            this.damagedEntity = node.path("damagedEntity").asText("");
            this.confidence = node.path("confidence").asDouble(0);
        } catch (Exception e) {
            // handle parse error
        }
    }

    public String getDamageCategory() {
        return damageCategory;
    }

    public String getDamagedEntity() {
        return damagedEntity;
    }

    public double getConfidence() {
        return confidence;
    }
}
