package com.accenture.claims.ai.application.tool.emailFlow;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.tool.DateParserTool;
import com.accenture.claims.ai.domain.model.emailParsing.Contacts;
import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import com.accenture.claims.ai.domain.model.emailParsing.Reporter;
import com.accenture.claims.ai.domain.repository.EmailParsingResultRepository;
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
import jakarta.validation.constraints.Email;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@ApplicationScoped
public class EmailParsingTool {

    @Inject ChatModel chatModel;
    @Inject SessionLanguageContext sessionLanguageContext;
    @Inject LanguageHelper languageHelper;
    @Inject
    DateParserTool dateParserTool;
    @Inject
    EmailParsingResultRepository emailParsingResultRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** ResponseFormat JSON (come prima). */
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
                                                            .addStringProperty("firstName").description("Nome del reporter; null se assente.")
                                                            .addStringProperty("lastName").description("Cognome del reporter; null se assente.")
                                                            .addProperty("contacts",
                                                                    JsonObjectSchema.builder()
                                                                            .addStringProperty("email").description("Email del Sender (from); null se assente o non valida.")
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

    /* ===== TOOL METHOD ===== */

    @Tool(
            name = "email_parsing_tool",
            value = "Parse an FNOL email and return a strict JSON with { policyNumber, incidentDate (ISO-8601), " +
                    "incidentLocation, reporter { firstName, lastName, contacts { email, mobile } } }. " +
                    "Parameters: sessionId, emailId, from, mailMessage. Returns: JSON string; missing/uncertain fields -> null."
    )
    public String email_parsing_tool(String sessionId, String emailId, String senderEmail, String mailMessage) {
        // Basic guard
        if ((senderEmail == null || senderEmail.isBlank()) && (mailMessage == null || mailMessage.isBlank())) {
            try {
                EmailParsingResult empty = new EmailParsingResult();
                empty.setEmailId(emailId);
                empty.setSessionId(sessionId);

                create(empty);
                return MAPPER.writeValueAsString(empty);
            } catch (Exception e) {
                return "{}";
            }
        }

        // lingua (se impostata in sessione)
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // prompt system (db o fallback)
        String sys = """
            You are an information extractor. Read the email content and extract ONLY the following fields:
            {
              "policyNumber": string|null,
              "incidentDate": string(ISO-8601)|null,
              "incidentLocation": string|null,
              "reporter": {
                "firstName": string|null,
                "lastName": string|null,
                "contacts": { "email": string|null, "mobile": string|null }
              }
            }
            Output MUST be a single JSON object EXACTLY with those keys.
            If a field is missing/unknown/invalid, set it to null.
            Do not invent values. Do not add/remove/rename fields. No comments. No code fences.
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.email.parsingPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of());
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }

        String user = """
            Sender (from): %s

            Email body:
            --------------------
            %s
            --------------------
            Return ONLY the JSON object.
            """.formatted(safe(senderEmail), safe(mailMessage));

        try {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(sys), UserMessage.from(user)))
                    .responseFormat(RESPONSE_FORMAT)
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String raw = chatResponse.aiMessage().text();
            String json = extractFirstJsonObject(raw);

            EmailParsingResult out = MAPPER.readValue(json, EmailParsingResult.class);
            sanitize(out, senderEmail);

            out.setEmailId(emailId);
            out.setSessionId(sessionId);

            postProcessIncidentDate(sessionId, mailMessage, out);
            create(out);

            return MAPPER.writeValueAsString(out);

        } catch (Exception e) {
            // Fallback: oggetto con tutti i campi a null + salvataggio
            try {
                EmailParsingResult fallback = new EmailParsingResult();
                fallback.setEmailId(emailId);
                fallback.setSessionId(sessionId);

                return MAPPER.writeValueAsString(fallback);
            } catch (Exception ex) {
                return "{}";
            }
        }
    }

    /* ===== Helpers ===== */

    private static String safe(String s) { return s == null ? "" : s; }

    private void create(EmailParsingResult data) {
        emailParsingResultRepository.persist(data);

        System.out.println("===================== PARTIAL RESULT ======================");
        System.out.println(data);
        System.out.println("===================== END PARTIAL RESULT ======================");
    }

    private static void sanitize(EmailParsingResult o, String senderEmail) {
        if (o == null) return;
        if (isBlank(o.getPolicyNumber())) o.setPolicyNumber(null);
        if (isBlank(o.getIncidentDate())) o.setIncidentDate(null);
        if (isBlank(o.getIncidentLocation())) o.setIncidentLocation(null);

        if (o.getReporter() == null) o.setReporter(new Reporter());
        if (isBlank(o.getReporter().getFirstName())) o.getReporter().setFirstName(null);
        if (isBlank(o.getReporter().getLastName())) o.getReporter().setLastName(null);

        if (o.getReporter().getContacts() == null) o.getReporter().setContacts(new Contacts());
        if (isBlank(o.getReporter().getContacts().getEmail())) o.getReporter().getContacts().setEmail(null);
        if (isBlank(o.getReporter().getContacts().getMobile())) o.getReporter().getContacts().setMobile(null);
        if (o.getReporter() != null && o.getReporter().getContacts() != null) {
            if (isBlank(o.getReporter().getContacts().getEmail())) {
                if (senderEmail != null) {
                    o.getReporter().getContacts().setEmail(senderEmail);
                }
            }
        }
    }

    private void postProcessIncidentDate(String sessionId, String mailMessage, EmailParsingResult out) {
        if (out == null) return;
        if (isBlank(out.getIncidentDate()) || !isIso8601Utc(out.getIncidentDate())) {
            try {
                String iso = dateParserTool.normalize(sessionId, mailMessage);
                if (iso != null && !iso.isBlank()) {
                    out.setIncidentDate(iso);
                }
            } catch (Exception ignore) {
                // resta a null
            }
        }
    }

    private static boolean isIso8601Utc(String s) {
        if (s == null) return false;
        String t = s.trim();
        // YYYY-MM-DDThh:mm:ssZ
        return t.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** Estrae il primo oggetto JSON da una risposta verbosa. */
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
