package com.accenture.claims.ai.application.agent.emailFlow;

import com.accenture.claims.ai.application.tool.PolicyFinderTool;
import com.accenture.claims.ai.application.tool.emailFlow.EmailWhatHappenedClassifierByPrompt;
import com.accenture.claims.ai.application.tool.emailFlow.EmailParsingTool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("{{systemPrompt}}") // var. risolta via @V
public interface FNOLEmailAssistantAgent {

    @ToolBox({
            EmailParsingTool.class,
            PolicyFinderTool.class,
            EmailWhatHappenedClassifierByPrompt.class,
    })
    String chat(@MemoryId String sessionId,
                @V("systemPrompt") String systemPrompt,
                @UserMessage String userMessage);

}
