package com.superagent.tools.commons;

import dev.langchain4j.agent.tool.ToolExecutor;
import java.util.List;
import java.util.Map;

/**
 * Simplified version of GenericMediaOCRAgentAsATool from Node project.
 * The Java version exposes a {@link ToolExecutor} that can be registered
 * to LangChain4j agents.
 */
public class GenericMediaOCRAgentTool implements ToolExecutor {
    private final String name;
    private final String description;
    private final String promptMessage;

    public GenericMediaOCRAgentTool(String name, String description, String promptMessage) {
        this.name = name;
        this.description = description;
        this.promptMessage = promptMessage;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        // TODO connect to vision model and return JSON string
        return "{}"; // placeholder
    }

    public String getPromptMessage() {
        return promptMessage;
    }
}
