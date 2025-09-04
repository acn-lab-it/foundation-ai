package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.application.tool.EmailParserTool;
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
//@InputGuardrails(PromptInjectionGuard.class)
/*@OutputGuardrails({
        NonEmptyOutputGuard.class,
        NoProgressWithoutToolGuard.class,
        //FinalOutputGuard.class
})*/
public interface FNOLEmailAssistantAgent {

    @ToolBox({
            EmailParserTool.class,
            MediaOcrAgent.class

    })
    String chat(@MemoryId String sessionId,
                @V("systemPrompt") String systemPrompt,
                @UserMessage String userMessage);

}
