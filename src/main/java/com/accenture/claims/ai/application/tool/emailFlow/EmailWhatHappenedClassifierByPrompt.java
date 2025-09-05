package com.accenture.claims.ai.application.tool.emailFlow;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.outbound.persistence.model.WhatHappenedEntity;
import com.accenture.claims.ai.adapter.outbound.persistence.repository.WhatHappenedRepositoryAdapter;
import com.accenture.claims.ai.adapter.outbound.persistence.repository.whatHappened.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.*;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class EmailWhatHappenedClassifierByPrompt {

    @Inject ChatModel chatModel;
    @Inject WhatHappenedRepositoryAdapter repo;
    @Inject FinalOutputJSONStore finalOutputJSONStore;

    private static final ObjectMapper M = new ObjectMapper();

    @Tool("""
          Classify a textual loss description and persist the result.
          @param sessionId current chat session
          @param emailId   current email id
          @param userText  raw description from the agent
          @param address   normalized/parsed incident location
          @return JSON ONLY:
                  { "whatHappenedCode": "...",
                    "whatHappenedContext": "...",
                    "claimClassGroup": "...",
                    "confidence": 0-1 }
          """)
    public String email_what_happened_classifier_by_prompt(String sessionId, String emailId, String userText, String address) throws JsonProcessingException {
        String json = classifyWhatHappened(userText);
        ObjectNode result = (ObjectNode) M.readTree(json);

        String code = textOrNull(result.get("whatHappenedCode"));
        String ctx  = textOrNull(result.get("whatHappenedContext"));

        ObjectNode patch = M.createObjectNode()
                .put("incidentLocation",    address);

        if (code == null) patch.putNull("whatHappenedCode");
        else patch.put("whatHappenedCode", code);

        if (ctx == null) patch.putNull("whatHappenedContext");
        else patch.put("whatHappenedContext", ctx);

        finalOutputJSONStore.put("final_output", sessionId, null, patch);

        if (emailId != null && !emailId.isBlank()) {
            finalOutputJSONStore.put("email_parsing_result", sessionId, emailId, null, patch);
        }

        return json;
    }

    public String classifyWhatHappened(String userText) {
        if (userText == null || userText.isBlank()) {
            return fallbackJson();
        }

        List<WhatHappenedEntity> allCats = repo.listAll();
        if (allCats.isEmpty()) {
            log.warn("WhatHappened – nessuna categoria presente a DB!");
            return fallbackJson();
        }

        List<WhatHappenedEntity> cats = allCats.stream()
                .filter(e -> e.getWhatHappenedContext() != null && e.getWhatHappenedContext().length() > 3)
                .limit(20)
                .collect(Collectors.toList());

        String catalog = cats.stream()
                .map(e -> "%s = %s [%s]".formatted(
                        e.getWhatHappenCode(),
                        e.getWhatHappenedContext(),
                        e.getClaimClassGroup()))
                .collect(Collectors.joining("\n"));

        SystemMessage sys = SystemMessage.from("""
            Sei un classificatore assicurativo esperto.
            Ogni stringa è strutturata come: "CODICE" = "DESCRIZIONE" [CLASSE_GRUPPO].
            Scegli ESATTAMENTE uno dei codici nella lista seguente per whatHappenedCode.
            Scegli ESATTAMENTE una delle descrizioni nella lista seguente per whatHappenedContext.
            Se nessun codice è adatto, usa null. Se non riesci a definire un codice, allora anche whatHappenedContext deve essere null.
            Rispondi esclusivamente con un JSON:
            { "whatHappenedCode": <CODICE>,
              "whatHappenedContext": <DESCRIZIONE>,
              "claimClassGroup": <CLASSE_GRUPPO>,
              "confidence": 0-1 }
            Non aggiungere testo extra.
        """);

        UserMessage usr = UserMessage.from("""
            <DESCRIZIONE_SINISTRO>
            %s
            </DESCRIZIONE_SINISTRO>

            <CATEGORIE>
            %s
            </CATEGORIE>
        """.formatted(userText.trim(), catalog));

        ChatResponse resp = chatModel.chat(
                ChatRequest.builder()
                        .messages(List.of(sys, usr))
                        .temperature(0.0)
                        .maxOutputTokens(256)
                        .build());

        String raw = resp.aiMessage().text();
        String json = extractJson(raw);
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        System.out.println(catalog);
        System.out.println(json);
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        return json != null ? json : fallbackJson();
    }

    private static String extractJson(String s) {
        if (s == null) return null;
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        return (i >= 0 && j > i) ? s.substring(i, j + 1) : null;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        if (s.isEmpty()) return null;
        if ("null".equalsIgnoreCase(s)) return null;   // evita "null" stringa
        if ("none".equalsIgnoreCase(s)) return null;   // opzionale, se il modello usa "NONE"
        return s;
    }

    private static String fallbackJson() {
        return """
               { "whatHappenedCode": null,
                 "whatHappenedContext": null,
                 "claimClassGroup": null,
                 "confidence":0.0 }""";
    }
}
