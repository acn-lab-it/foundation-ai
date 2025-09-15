package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.output.JsonSchemas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.lang.reflect.InvocationTargetException;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@ApplicationScoped
public class StepParseMessageTool {
    @Inject
    ChatModel chatModel;
    @Inject
    ClaimSubmissionProgressRepository claimSubmissionProgressRepository;


    @Tool(value = """
               Questo tool accetta in input un messaggio di chat dell'utente e un progresso e ritorna un progresso aggiornato
            """)
    public void parseStepMessage(ClaimSubmissionStep step, String userMessage, String sessionId) {
        ResponseFormat responseFormat = getResponseFormat();
        ChatResponse resp = chatModel.chat(ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(
                        SystemMessage.from("""
                                Sei un information extractor, devi prendere in input il messaggio utente ed estrapolare solamente queste informazioni e collocarle sulla struttura di output.
                                Rispondi solamente con il JSON, niente code fences, nessun commento.
                                Formato risposta: %s
                                """.formatted(responseFormat.jsonSchema())), //TODO use a proper format
                        UserMessage.from(userMessage)
                )
                .temperature(0.0)
                .build());

        var result = parse(resp.aiMessage().text());
        var progress = claimSubmissionProgressRepository.findBySessionId(sessionId).getParsingResult();
        for (String field : step.getOwnedFields()) {
            try {
                Object byFieldName;
                try {
                    byFieldName = Utils.getByFieldName(result, field);
                } catch (NoSuchMethodException e) {
                    continue;
                }
                if (byFieldName != null) {
                    Utils.setByFieldName(progress, field, byFieldName);
                }
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        claimSubmissionProgressRepository.upsertBySessionId(sessionId, result);
    }

    private ParsingResult parse(String raw) {
        try {
            return new ObjectMapper().readValue(raw, ParsingResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseFormat getResponseFormat() {
        return ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(
                        JsonSchemas.jsonSchemaFrom(
                                ParsingResult.class).get()
                ).build();
    }
}
