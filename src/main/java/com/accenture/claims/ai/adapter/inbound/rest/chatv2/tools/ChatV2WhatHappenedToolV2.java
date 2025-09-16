package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.outbound.persistence.model.WhatHappenedEntity;
import com.accenture.claims.ai.adapter.outbound.persistence.repository.WhatHappenedRepositoryAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
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
public class ChatV2WhatHappenedToolV2 {

    @Inject
    ChatModel chatModel;
    @Inject
    WhatHappenedRepositoryAdapter repo;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private static final ObjectMapper M = new ObjectMapper();

    @Tool("""
          Classify a textual loss description and persist the result.
          @param sessionId  current chat session
          @param userText   raw description from the agent
          @param address    incident location
          @Return a JSON ONLY:
                  { "whatHappenedCode": "...",
                    "whatHappenedContext": "...",
                    "claimClassGroup": "...",
                    "confidence": 0‑1 }
                  ""
      """)
    public String classifyAndSaveV2(String sessionId, String userText, String address) throws JsonProcessingException {

        String json = classifyWhatHappened(userText);   // ← metodo esistente
        ObjectNode result = (ObjectNode) M.readTree(json);

        // Patch FINAL_OUTPUT
        ObjectNode patch = M.createObjectNode()
                .put("whatHappenedCode", result.path("whatHappenedCode").asText())
                .put("whatHappenedContext", result.path("whatHappenedContext").asText())
                .put("incidentLocation", address);

        finalOutputJSONStore.put("final_output", sessionId, "", patch);

        return json;
    }

    public String classifyWhatHappened(String userText) {
        if (userText == null || userText.isBlank()) {
            return fallbackJson();
        }

        // 1) categorie dal DB
        List<WhatHappenedEntity> allCats = repo.listAll();
        if (allCats.isEmpty()) {
            log.warn("WhatHappened – nessuna categoria presente a DB!");
            return fallbackJson();
        }

        // Filter out problematic entries and limit to 20 most relevant categories
        List<WhatHappenedEntity> cats = allCats.stream()
                .filter(e -> e.getWhatHappenedContext() != null
                        && e.getWhatHappenedContext().length() > 3)  // Filter out incomplete entries
                .limit(20)  // Limit to 20 categories
                .collect(Collectors.toList());

        // elenco codice = descrizione (gruppo)
        String catalog = cats.stream()
                .map(e -> "%s = %s [%s]"
                        .formatted(e.getWhatHappenCode(),
                                e.getWhatHappenedContext(),
                                e.getClaimClassGroup()))
                .collect(Collectors.joining("\n"));

        // 3) prompt - simplified system message
        SystemMessage sys = SystemMessage.from("""
        Sei un classificatore assicurativo esperto.
        Ogni stringa è strutturata come: "CODICE" = "DESCRIZIONE" [CLASSE_GRUPPO].
        Scegli ESATTAMENTE uno dei codici nella lista seguente per whatHappenedCode.
        Scegli ESATTAMENTE una delle descrizioni nella lista seguente per whatHappenedContext.
        Se nessun codice è adatto, usa UNKNOWN.
        Rispondi **esclusivamente** con un JSON:
        { "whatHappenedCode": <CODICE>,
          "whatHappenedContext": <DESCRIZIONE>,
          "claimClassGroup": <CLASSE_GRUPPO>,
          "confidence": 0‑1 }
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
                        .temperature(0.0) // classificazione deterministica
                        .maxOutputTokens(256)
                        .build());

        String raw = resp.aiMessage().text();
        String json = extractJson(raw);
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        System.out.println(raw);
        System.out.println(json);
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        return json != null ? json : fallbackJson();
    }

    /* ---------------- util ---------------- */

    private static String extractJson(String s) {
        if (s == null) return null;
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        return (i >= 0 && j > i) ? s.substring(i, j + 1) : null;
    }

    private static String fallbackJson() {
        return """
               { "whatHappenedCode":"UNKNOWN",
                 "whatHappenedContext":"UNKNOWN",
                 "claimClassGroup":"UNKNOWN",
                 "confidence":0.0 }""";
    }
}
