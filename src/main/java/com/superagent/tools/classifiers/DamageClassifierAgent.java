package com.superagent.tools.classifiers;

import com.superagent.tools.commons.GenericMediaOCRAgentTool;

/**
 * Port of damageClassifierAgent.js
 */
public class DamageClassifierAgent extends GenericMediaOCRAgentTool {
    public DamageClassifierAgent(Config config) {
        super(config.toolName, config.description, config.promptMessage);
    }

    public static class Config {
        public String toolName;
        public String description;
        public String promptMessage;
    }
}
