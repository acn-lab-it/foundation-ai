package com.accenture.claims.ai.application.agent.emailFlow;

import com.accenture.claims.ai.adapter.outbound.persistence.repository.whatHappened.WhatHappenedClassifierByPrompt;
import com.accenture.claims.ai.application.tool.emailFlow.EmailMediaAttachmentParser;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("{{systemPrompt}}")
public interface EmailMediaAgent {

    @ToolBox({
            EmailMediaAttachmentParser.class,
    })
    String chat(@MemoryId String sessionId,
                @V("systemPrompt") String systemPrompt,
                @UserMessage String userMessage);
}