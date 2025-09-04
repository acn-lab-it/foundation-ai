package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@ApplicationScoped
public class EmailParsingTool {

    @Inject ChatModel chatModel;
    @Inject SessionLanguageContext sessionLanguageContext;
    @Inject LanguageHelper languageHelper;
    @Inject FinalOutputJSONStore finalOutputJSONStore;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** ResponseFormat JSON (stesso schema che avevi nell’endpoint). */
    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(JSON)
            .jsonSchema(
                    JsonSchema.builder()
                            .name("FnolEmailExtraction")
                            .rootElement(
                                    JsonObjectSchema.builder()
                                            .addStringProperty("policyNumber").description("Numero di polizza; string o null se assente.")
                                            .addStringProperty("incidentDate").description("Data/ora del sinistro in ISO 8601; null se assente.")
                                            .addStringProperty("incidentLocation").description("Luogo del sinistro; null se assente.")
                                            .addProperty("reporter",
                                                    JsonObjectSchema.builder()
                                                            .addStringProperty("name").description("Nome del reporter; null se assente.")
                                                            .addStringProperty("surname").description("Cognome del reporter; null se assente.")
                                                            .addProperty("contacts",
                                                                    JsonObjectSchema.builder()
                                                                            .addStringProperty("email").description("Email; null se assente o non valida.")
                                                                            .addStringProperty("mobile").description("Numero di cellulare; null se assente.")
                                                                            .required("email", "mobile")
                                                                            .build()
                                                            )
                                                            .required("contacts")
                                                            .build()
                                            )
                                            .required("policyNumber", "incidentDate", "reporter", "incidentLocation")
                                            .build()
                            )
                            .build()
            )
            .build();

    /* ===== DTO ===== */

    public static final class FnolEmailRequest {
        public String from;
        public String mailMessage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Contacts {
        public String email;
        public String mobile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Reporter {
        public String name;
        public String surname;
        public Contacts contacts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class FnolEmailExtraction {
        public String policyNumber;
        public String incidentDate;
        public String incidentLocation;
        public Reporter reporter;
    }

    /* ===== TOOL METHOD ===== */

    @Tool(
            name = "fnol_email_parsing_tool",
            value = "Parse an FNOL email and return a strict JSON with { policyNumber, incidentDate (ISO-8601), incidentLocation, reporter { name, surname, contacts { email, mobile } } }. " +
                    "Parameters: sessionId, from, mailMessage. Returns: JSON string; missing/uncertain fields -> null."
    )
    public String extract(String sessionId, String from, String mailMessage) {
        // Basic guard
        if ((from == null || from.isBlank()) && (mailMessage == null || mailMessage.isBlank())) {
            try {
                return MAPPER.writeValueAsString(nulls());
            } catch (Exception e) {
                return "{}";
            }
        }

        // (opzionale) lingua per futuri prompt multilingua
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // Se ci sta template in DB, prova a caricarlo a step dopo; altrimenti abbiamo fallback al prompt hardcoded
        String sys = """
            You are an information extractor. Read the email content and extract ONLY the following fields:
            {
              "policyNumber": string|null,
              "incidentDate": string(ISO-8601)|null,
              "incidentLocation": string|null,
              "reporter": {
                "name": string|null,
                "surname": string|null,
                "contacts": { "email": string|null, "mobile": string|null }
              }
            }
            Output MUST be a single JSON object EXACTLY with those keys.
            If a field is missing/unknown/invalid, set it to null.
            Do not invent values. Do not add/remove/rename fields. No comments. No code fences.
            """;

        try {
            if (languageHelper != null) {
                // Se esiste una key tipo "fnol.email.extractor"
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.email.parsingPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of());
                }
            }
        } catch (Exception ignore) {
            // fallback al sys hardcoded
        }

        String user = """
            Sender (from): %s

            Email body:
            --------------------
            %s
            --------------------
            Return ONLY the JSON object.
            """.formatted(safe(from), safe(mailMessage));

        try {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(sys),
                            UserMessage.from(user)
                    ))
                    .responseFormat(RESPONSE_FORMAT)
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String raw = chatResponse.aiMessage().text();
            String json = extractFirstJsonObject(raw);

            FnolEmailExtraction out = MAPPER.readValue(json, FnolEmailExtraction.class);
            sanitize(out);

            // Persistenza nel FinalOutputJSONStore (come patch intero oggetto)
            if (finalOutputJSONStore != null) {
                ObjectNode patch = MAPPER.valueToTree(out);
                finalOutputJSONStore.put("final_output", sessionId, null, patch);
            }

            return MAPPER.writeValueAsString(out);

        } catch (Exception e) {
            // Fallback: oggetto con tutti i campi a null
            try {
                FnolEmailExtraction fallback = nulls();
                if (finalOutputJSONStore != null) {
                    ObjectNode patch = MAPPER.valueToTree(fallback);
                    finalOutputJSONStore.put("final_output", sessionId, null, patch);
                }
                return MAPPER.writeValueAsString(fallback);
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    /* ===== Helpers ===== */

    private static String safe(String s) { return s == null ? "" : s; }

    private static FnolEmailExtraction nulls() {
        FnolEmailExtraction o = new FnolEmailExtraction();
        o.policyNumber = null;
        o.incidentDate = null;
        o.incidentLocation = null;
        o.reporter = new Reporter();
        o.reporter.name = null;
        o.reporter.surname = null;
        o.reporter.contacts = new Contacts();
        o.reporter.contacts.email = null;
        o.reporter.contacts.mobile = null;
        return o;
    }

    private static void sanitize(FnolEmailExtraction o) {
        if (o == null) return;
        if (isBlank(o.policyNumber)) o.policyNumber = null;
        if (isBlank(o.incidentDate)) o.incidentDate = null;
        if (isBlank(o.incidentLocation)) o.incidentLocation = null;

        if (o.reporter == null) o.reporter = new Reporter();
        if (isBlank(o.reporter.name)) o.reporter.name = null;
        if (isBlank(o.reporter.surname)) o.reporter.surname = null;

        if (o.reporter.contacts == null) o.reporter.contacts = new Contacts();
        if (isBlank(o.reporter.contacts.email)) o.reporter.contacts.email = null;
        if (isBlank(o.reporter.contacts.mobile)) o.reporter.contacts.mobile = null;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** Estrae il primo oggetto JSON da una risposta verbosa del modello. */
    private static String extractFirstJsonObject(String s) {
        if (s == null) return "{}";
        int start = s.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        return s.substring(start);
    }
}
