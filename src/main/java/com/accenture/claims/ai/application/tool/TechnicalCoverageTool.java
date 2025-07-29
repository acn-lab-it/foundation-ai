package com.accenture.claims.ai.application.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.regex.Pattern;

@ApplicationScoped
public class TechnicalCoverageTool {

    @Inject ChatModel chatModel;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // set di default (puoi estenderlo in futuro, ma l’output deve essere una sola voce)
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("MOTOR", "PROPERTY", "LIABILITY", "UNKNOWN");

    // per fallback di parsing veloce
    private static final Pattern DEFAULT_TOKEN =
            Pattern.compile("\\b(MOTOR|PROPERTY|LIABILITY|UNKNOWN)\\b", Pattern.CASE_INSENSITIVE);

    // ==== DTO (obbligatorio costruttore vuoto + getter/setter per il mapping del tool) ====
    public static class ClassifyRequest {
        private String sessionId;      // opzionale
        private String description;    // OBBLIGATORIO: descrizione del danno
        private String policyDomain;
        private List<String> categories; // opzionale: override dell’elenco

        public ClassifyRequest() {}

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getPolicyDomain() {return policyDomain; }
        public void setPolicyDomain(String policyDomain) { this.policyDomain = policyDomain; }

        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
    }

    @Tool("""
      TechnicalCoverageTool.CheckPolicyCoverage:
      Passa un oggetto 'request' con:
      {
        "sessionId": "<opzionale>",
        "description": "<testo che descrive il danno>",
        "policyDomain": "<dominio attuale della polizza: es. MOTOR|PROPERTY|LIABILITY|MULTIRISK|UNKNOWN>",
        "categories": ["MOTOR","PROPERTY","LIABILITY","UNKNOWN"]
      }
      Ritorna un boolean: true se coerente con la polizza (MULTIRISK copre tutto tranne MOTOR), altrimenti false.
      """)
    public Boolean CheckPolicyCoverage(ClassifyRequest request) {
        try {
            // Se non ho descrizione, non posso validare
            if (request == null || isBlank(request.getDescription())) {
                return false;
            }

            List<String> cats = (request.getCategories() != null && !request.getCategories().isEmpty())
                    ? upper(request.getCategories())
                    : DEFAULT_CATEGORIES;

            String sys = "Sei un classificatore assicurativo. " +
                    "Ti verrà data una descrizione di sinistro. " +
                    "Scegli ESATTAMENTE UNA categoria fra: " + String.join(" | ", cats) + ". " +
                    "Rispondi SOLO con la categoria in MAIUSCOLO. " +
                    "Se nessuna è adatta, rispondi UNKNOWN.";

            String user = "Descrizione: " + request.getDescription() +
                    "\nCategorie possibili: " + String.join(", ", cats) +
                    "\nRispondi con UNA SOLA PAROLA.";

            ChatResponse res = chatModel.chat(ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(sys), UserMessage.from(user)))
                    .temperature(0.0)
                    .maxOutputTokens(5)
                    .build());

            String raw = (res.aiMessage() != null && res.aiMessage().text() != null)
                    ? res.aiMessage().text().trim()
                    : "";

            String claimDomain = extractLabel(raw, cats);
            return coverageFor(request.getPolicyDomain(), claimDomain);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---- nuova regola di copertura ----
    private static boolean coverageFor(String policyDomain, String claimDomain) {
        if (policyDomain == null || claimDomain == null) return false;
        String p = policyDomain.trim().toUpperCase(Locale.ROOT);
        String c = claimDomain.trim().toUpperCase(Locale.ROOT);

        if ("MULTIRISK".equals(p)) {
            // MULTIRISK copre tutto tranne MOTOR; UNKNOWN non è accettabile
            if ("UNKNOWN".equals(c)) return false;
            return !"MOTOR".equals(c);
        }
        return p.equals(c);
    }


    // ===== helpers =====
    private static String extractLabel(String raw, List<String> cats) {
        if (raw == null) return "UNKNOWN";

        // match esatto su una delle categorie (case-insensitive)
        for (String c : cats) {
            if (raw.equalsIgnoreCase(c)) return c.toUpperCase(Locale.ROOT);
        }
        // fallback: se sono le default, prova regex
        var m = DEFAULT_TOKEN.matcher(raw);
        if (m.find()) return m.group(1).toUpperCase(Locale.ROOT);

        // ultimo fallback: cerca il token come substring
        String upper = raw.toUpperCase(Locale.ROOT);
        for (String c : cats) {
            if (upper.contains(c)) return c.toUpperCase(Locale.ROOT);
        }
        return "UNKNOWN";
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static List<String> upper(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s != null && !s.isBlank()) out.add(s.trim().toUpperCase(Locale.ROOT));
        }
        return out.isEmpty() ? DEFAULT_CATEGORIES : out;
    }

    private static String json(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
