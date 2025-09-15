package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.output.JsonSchemas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@ApplicationScoped
public class StepParseMessageTool {
    @Inject
    ChatModel chatModel;


    @Tool(value = """
               Questo tool accetta in input un messaggio di chat dell'utente e un progresso e ritorna un progresso aggiornato
            """)
    public ClaimSubmissionProgress getStepMessage(String userMessage, ClaimSubmissionProgress progress) {
        ChatResponse resp = chatModel.chat(ChatRequest.builder()
                .responseFormat(getResponseFormat())
                .build());

        var result = parse(resp.aiMessage().text());

        return result;


    }

    private ClaimSubmissionProgress parse(String raw) {
        try {
            return new ObjectMapper().readValue(raw, ClaimSubmissionProgress.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseFormat getResponseFormat() {
        return ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(
                        JsonSchemas.jsonSchemaFrom(
                                EmailParsingResult.class).get()
                ).build();
    }
}
