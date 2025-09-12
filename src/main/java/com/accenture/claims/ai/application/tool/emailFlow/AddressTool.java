package com.accenture.claims.ai.application.tool.emailFlow;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.*;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@ApplicationScoped
public class AddressTool {
    @Inject
    ChatModel chatModel;

    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject LanguageHelper languageHelper;

    @Tool(
            name = "address_verification_tool",
            value = "Tell whether if the provided address contains the street name and the house number. Parameters: sessionId, address. Returns: 1 if the adress contains the street name and the house number, 0 otherwise."
    )
    public boolean address_verification_tool(String sessionId, String address) {
        // Basic guard
        if (address == null || address.isBlank()) {
            return false;
        }

        // lingua (se impostata in sessione)
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // prompt system (db o fallback)
        String sys = """
            You are an information extractor. Tell me if the provided address contains the street name and the house number.
            Output MUST be a single number: 1 if the address contains the street name and the house number, 0 otherwise.
            Do not invent values. Do not add anything else: just 1 or 0. No comments. No code fences.
            Address: {{address}}
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.address.verificationPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("address", address));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }


        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(address))).build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        if(raw.equals("1")) {
            return true;
        }
        if(raw.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("LLM did not return 0 or 1: '" + raw + "'");
    }

    @Tool(
            name = "city_verification_tool",
            value = "Tell whether if the provided address contains the city name. Returns: 1 if the adress contains the city name, 0 otherwise."
    )
    public boolean city_verification_tool(String sessionId, String address) {
        // Basic guard
        if (address == null || address.isBlank()) {
            return false;
        }

        // lingua (se impostata in sessione)
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // prompt system (db o fallback)
        String sys = """
            You are an information extractor. Tell me if the provided address contains the city name.
            Output MUST be a single number: 1 if the address contains the city name, 0 otherwise.
            Do not invent values. Do not add anything else: just 1 or 0. No comments. No code fences.
            Provided address: {{address}}
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.city.verificationPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("address", address));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }


        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(address))).build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        if(raw.equals("1")) {
            return true;
        }
        if(raw.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("LLM did not return 0 or 1: '" + raw + "'");

    }

    @Tool(name = "format_address_tool", value = """
                Take the input address and convert it into S42 (ISO 19160) format.
                Do not add anything else. No comments. No code fences.
                Provided address: {{address}}
            """)
    public String formatAddress(String sessionId, String address){
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Address cannot be null or empty");
        }

        // lingua (se impostata in sessione)
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // prompt system (db o fallback)
        String sys = """
            You are an information converter. Transform the input address into the S42 (ISO 19160) format.
             No comments. No code fences. Reply with a compliant JSON format. Do not change field names, not even the casing. Do not add anything else.
             Here is the JSON schema:
             {
              "addressLine": string|null,
              "postCode": string|null,
              "city": string|null,
              "province": string|null,
              "country": string|null
            }
            Provided address: {{address}}
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.address.standardizationPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("address", address));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }


        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(address)))
                .responseFormat(
                        ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(
                                        JsonSchema.builder()
                                                .name("addressFormat")
                                                .rootElement(
                                                        JsonObjectSchema.builder()
                                                                .addStringProperty("addressLine")
                                                                .addStringProperty("postCode")
                                                                .addStringProperty("city")
                                                                .addStringProperty("province")
                                                                .addStringProperty("country").build())
                                                .build()
                                )
                .build()).build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        //Verify if the address is actually in S42 format
        if(!isISO19160Format(raw)) {
            throw new IllegalArgumentException("Wrong AI response while converting address to S42 format. Response:" + raw);
        }

        return raw;

    }

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "addressLine", "postCode", "city", "province", "country"
    );

    public static boolean isISO19160Format(String input) {
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode rootNode = mapper.readTree(input);

            // Deve essere un oggetto JSON
            if (!rootNode.isObject()) {
                return false;
            }

            ObjectNode objNode = (ObjectNode) rootNode;

            // Raccoglie tutti i campi presenti nell'oggetto
            Iterator<String> fieldNames = objNode.fieldNames();
            Set<String> actualFields = new HashSet<>();

            while (fieldNames.hasNext()) {
                actualFields.add(fieldNames.next());
            }

            // Controlla che ci siano esattamente i campi attesi, nessuno in più o in meno
            return actualFields.equals(EXPECTED_FIELDS);

        } catch (Exception e) {
            return false; // Non è un JSON valido
        }
    }


}
