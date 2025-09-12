package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class StepMessageTool {

    @Tool(value = """
            
    """)
    public String getStepMessage() {
        return "This is a step message";
    }
}
