package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.application.tool.SummaryTool;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.accenture.claims.ai.application.tool.AdministrativeCheckTool;
import com.accenture.claims.ai.application.tool.DateParserTool;
import com.accenture.claims.ai.domain.repository.WhatHappenedRepository;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("{{systemPrompt}}")
public interface FNOLAssistantAgent {

    @ToolBox({
        PolicyRepository.class,
        AdministrativeCheckTool.class,
        DateParserTool.class,
        WhatHappenedRepository.class,
        MediaOcrAgent.class,
        SummaryTool.class
    })
    /*
     * @V - Annotazione per passarsi parametri dinamici, in questo caso passiamo systemPrompt dall'esterno e lo iniettiamo in:
     * @SystemMessage("{{systemPrompt}}")
     */
    String chat( @MemoryId String sessionId, @V("systemPrompt") String systemMessage, @UserMessage String userMessage);

}
