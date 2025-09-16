package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class StepMessageTool {

    @Inject
    ChatModel chatModel;
    @Inject
    NextStepAdapter nextStepAdapter;
    @Inject
    ClaimSubmissionProgressRepository claimSubmissionProgressRepository;

    @Tool(value = """
                Compose a professional message asking the reporter the missing data of .
                Parameters:
                - sessionId: current session id (string)
                - step: current step (ClaimSubmissionStep, may be null or empty)
            
                - locale: language hint like "it" or "en" (string, optional)
                Returns: a single string with the full message
            
            """,
            name = "getStepMessage")
    public String getStepMessage(String sessionId, ClaimSubmissionStep step, String locale) {
        var progress = claimSubmissionProgressRepository.findBySessionId(sessionId);
        if (step.isComplete(progress)) {
            step = nextStepAdapter.nextStep(progress);
        }
        String sys = """
                You are Happy Claim, a chatbot that fills in information through interactions with a human user.
                You must write a courteous and professional chat message in order to gather information about an insurance claim.
                Please, reply in this language: %s
                Here are the pieces of information you need to ask about in human like language.
                %s
                """.formatted(locale, String.join(", ", step.getValidator().getIncompleteFields(progress)));

        ChatResponse resp = chatModel.chat(ChatRequest.builder()
                .messages(java.util.List.of(SystemMessage.from(sys)))
                .build());

        return resp.aiMessage().text();
    }
}
