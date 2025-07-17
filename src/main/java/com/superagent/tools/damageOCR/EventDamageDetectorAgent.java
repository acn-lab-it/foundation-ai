package com.superagent.tools.damageOCR;

import com.superagent.tools.commons.GenericMediaOCRAgentTool;

/**
 * Port of eventDamageDetectorAgent.js
 */
public class EventDamageDetectorAgent extends GenericMediaOCRAgentTool {
    public EventDamageDetectorAgent(Config config) {
        super(config.toolName, config.description, config.promptMessage);
    }

    public static class Config {
        public String toolName;
        public String description;
        public String promptMessage;
    }
}
