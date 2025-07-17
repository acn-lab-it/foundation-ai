package com.superagent.tools.classifiers;

import com.superagent.tools.commons.GenericMediaOCRAgentTool;

/**
 * Port of damagedPropertyClassifierAgent.js
 */
public class DamagedPropertyClassifierAgent extends GenericMediaOCRAgentTool {
    public DamagedPropertyClassifierAgent(Config config) {
        super(config.toolName, config.description, config.promptMessage);
    }

    public static class Config {
        public String toolName;
        public String description;
        public String promptMessage;
    }
}
