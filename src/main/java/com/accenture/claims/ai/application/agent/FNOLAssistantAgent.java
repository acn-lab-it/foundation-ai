package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.adapter.outbound.persistence.repository.whatHappened.WhatHappenedClassifierByPrompt;
import com.accenture.claims.ai.application.tool.*;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.accenture.claims.ai.guardrails.FinalOutputGuard;
import com.accenture.claims.ai.guardrails.NoProgressWithoutToolGuard;
import com.accenture.claims.ai.guardrails.NonEmptyOutputGuard;
import com.accenture.claims.ai.guardrails.PromptInjectionGuard;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("{{systemPrompt}}") // var. risolta via @V
//@InputGuardrails(PromptInjectionGuard.class)
@OutputGuardrails({
        NonEmptyOutputGuard.class,
        NoProgressWithoutToolGuard.class,
        //FinalOutputGuard.class
})
public interface FNOLAssistantAgent {

    @ToolBox({
            PolicyRepository.class,
            AdministrativeCheckTool.class,
            DateParserTool.class,
            MediaOcrAgent.class,
            SpeechToTextAgent.class,
            SummaryTool.class,
            PolicyFinderTool.class,
            TechnicalCoverageTool.class,
            WhatHappenedClassifierByPrompt.class
    })
    String chat(@MemoryId String sessionId,
                @V("systemPrompt") String systemPrompt,
                @UserMessage String userMessage);

}
