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

    @Tool(value = """
                Compose a professional message asking the reporter the missing data of .
                Parameters:
                - sessionId: current session id (string)
                - step: current step (ClaimSubmissionStep, may be null or empty)
            
                - locale: language hint like "it" or "en" (string, optional)
                Returns: a single string with the full message
            
            """,
            name = "getStepMessage")
    public String getStepMessage(String sessionId, ClaimSubmissionStep step, ClaimSubmissionProgress progress, String locale) {
        //Dato uno step ed il progress associato stampo il messaggio facendo check che i dati siano corretti
        if (step.isComplete(progress)) {
            step = nextStepAdapter.nextStep(progress);
        }
        step.getValidator().getIncompleteFields(progress);
        String sys = """
                Write a courteous and professional message that tell to the assistant to retrieve and write the missing data.
                - 
                - the 'step' field indicates an enum that indicates the step we are in, if complete ignore the field has already been viewed otherwise for the type entered check that the fields in the 'fields' list are filled
                - Output language should match the locale if provided (e.g., "it" or "en"). Default to English.
                """;

        ChatResponse resp = chatModel.chat(ChatRequest.builder()
                .messages(java.util.List.of(SystemMessage.from(sys)))
                .temperature(0.0)
                .maxOutputTokens(300)
                .build());

        return resp.aiMessage().text();
    }
}
