package com.accenture.claims.ai.application.tool.emailFlow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class EmailParser2Tool {

    public static final String SYSTEM_PROMPT = """
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
            Input parameter: sessionId, sender, rawEmail
            """;
    @Inject ChatModel chatModel;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** JSON Schema per ResponseFormat. */
    private static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON)
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
        public String incidentDate;      // ISO 8601 o null
        public String incidentLocation;  // <-- adesso a root
        public Reporter reporter;
    }


    /* ===== Logica comune ===== */
    @Tool(
            name = "parseEmail2",
            value = SYSTEM_PROMPT
    )
    public FnolEmailExtraction doExtract(String sessionId, String sender, String rawEmail) {
        if (sender == null || rawEmail == null || isBlank(sender) || isBlank(rawEmail)) {
            return nulls();
        }

        String user = """
            Sender (from): %s

            Email body:
            --------------------
            %s
            --------------------
            Return ONLY the JSON object.
            """.formatted(safe(sender), safe(rawEmail));

        try {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(SYSTEM_PROMPT),
                            UserMessage.from(user)
                    ))
                    .responseFormat(RESPONSE_FORMAT)
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String raw = chatResponse.aiMessage().text();

            String json = extractFirstJsonObject(raw);

            FnolEmailExtraction out = MAPPER.readValue(json, FnolEmailExtraction.class);
            sanitize(out);
            return out != null ? out : nulls();

        } catch (Exception e) {
            return nulls();
        }
    }

    /* ===== Helpers ===== */
    private static String safe(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static FnolEmailExtraction nulls() {
        FnolEmailExtraction o = new FnolEmailExtraction();
        o.policyNumber = null;
        o.incidentDate = null;
        o.incidentLocation = null;  // <-- root
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
 