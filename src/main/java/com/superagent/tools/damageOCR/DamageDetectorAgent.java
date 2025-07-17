package com.superagent.tools.damageOCR;

import com.superagent.tools.commons.GenericMediaOCRAgentTool;

/**
 * Port of damageDetectorAgent.js
 */
public class DamageDetectorAgent extends GenericMediaOCRAgentTool {
    public DamageDetectorAgent(Config config) {
        super(config.toolName, config.description, config.promptMessage);
    }

    public static class Config {
        public String toolName;
        public String description;
        public String promptMessage;
    }
}
