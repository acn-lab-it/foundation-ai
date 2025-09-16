package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

@ApplicationScoped
public class ChatV2AddressToolV2 {
    @Inject
    ChatModel chatModel;

    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject 
    LanguageHelper languageHelper;

    @Tool(
            name = "address_verification_tool_v2",
            value = "Tell whether if the provided address contains the street name and the house number. Parameters: sessionId, address. Returns: 1 if the address contains the street name and the house number, 0 otherwise."
    )
    public boolean address_verification_tool_v2(String sessionId, String address) {
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
            name = "city_verification_tool_v2",
            value = "Tell whether if the provided address contains the city name. Returns: 1 if the address contains the city name, 0 otherwise."
    )
    public boolean city_verification_tool_v2(String sessionId, String address) {
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

    @Tool(name = "format_address_tool_v2", value = """
                Take the input address and convert it into S42 (ISO 19160) format.
                Do not add anything else. No comments. No code fences.
                Provided address: {{address}}
            """)
    public String formatAddressV2(String sessionId, String address){
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

    @Tool("Extract only street information from address (e.g., 'Via Roma 123' -> 'Via Roma 123'). Parameters: sessionId, raw.")
    public String extractStreetOnlyV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String lang = getLanguage(sessionId);

        // Prompt specifico per estrarre solo la via (SENZA numero civico)
        String systemPrompt = String.format("""
            You are an expert at extracting ONLY street name from text (WITHOUT house numbers).
            Extract the street name from the user message and return ONLY the street name part.
            
            Examples:
            - "Via Roma 123, Milano" -> "Via Roma"
            - "Corso Italia 45, Roma" -> "Corso Italia"
            - "Linzer Str. 10, Wien" -> "Linzer Str."
            - "123 Main Street, New York" -> "Main Street"
            - "Via Pacini 16" -> "Via Pacini"
            
            Return ONLY the street name (no numbers), or null if no street is found.
            """);

        try {
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(raw)
            )).aiMessage().text();
            
            if (response != null && !response.trim().equals("null") && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            // Fallback to regex patterns
            return extractStreetWithRegex(raw);
        }
        
        return null;
    }

    @Tool("Extract only city information from address (e.g., 'Via Roma, Milano' -> 'Milano'). Parameters: sessionId, raw.")
    public String extractCityOnlyV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String lang = getLanguage(sessionId);

        // Prompt specifico per estrarre solo la città
        String systemPrompt = String.format("""
            You are an expert at extracting ONLY city information from text.
            Extract the city name from the user message and return ONLY the city part.
            
            Examples:
            - "Via Roma 123, Milano" -> "Milano"
            - "Corso Italia 45, Roma" -> "Roma"
            - "Linzer Str. 10, Wien" -> "Wien"
            - "123 Main Street, New York" -> "New York"
            
            Return ONLY the city name, or null if no city is found.
            """);

        try {
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(raw)
            )).aiMessage().text();
            
            if (response != null && !response.trim().equals("null") && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            // Fallback to regex patterns
            return extractCityWithRegex(raw);
        }
        
        return null;
    }

    private String extractStreetWithRegex(String raw) {
        // Pattern per "Via Roma 123", "Corso Italia 45", "123 Main Street"
        Pattern streetPattern = Pattern.compile("([A-Za-z\\s]+\\s+[A-Za-z]+\\s*\\d+|\\d+\\s+[A-Za-z\\s]+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Via|Viale|Corso|Piazza))");
        Matcher matcher = streetPattern.matcher(raw);
        
        if (matcher.find()) {
            String street = matcher.group(1).trim();
            // Rimuovi numeri dalla fine per evitare duplicazioni
            street = street.replaceAll("\\s+\\d+\\s*$", "").trim();
            return street;
        }
        
        return null;
    }

    private String extractCityWithRegex(String raw) {
        // Pattern per città dopo virgola o alla fine
        Pattern cityPattern = Pattern.compile(",\\s*([A-Za-z\\s]+)(?:\\s*$|\\s*,)");
        Matcher matcher = cityPattern.matcher(raw);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Pattern per città alla fine senza virgola
        Pattern cityEndPattern = Pattern.compile("\\s+([A-Za-z\\s]+)$");
        matcher = cityEndPattern.matcher(raw);
        if (matcher.find()) {
            String potentialCity = matcher.group(1).trim();
            // Escludi numeri e parole troppo corte
            if (potentialCity.length() > 2 && !potentialCity.matches(".*\\d.*")) {
                return potentialCity;
            }
        }
        
        return null;
    }

    private String getLanguage(String sessionId) {
        try {
            if (sessionLanguageContext != null) {
                String lang = sessionLanguageContext.getLanguage(sessionId);
                if (lang != null && !lang.isBlank()) {
                    return lang;
                }
            }
        } catch (Exception e) {
            // Fallback to default
        }
        return "it"; // Default italiano
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
