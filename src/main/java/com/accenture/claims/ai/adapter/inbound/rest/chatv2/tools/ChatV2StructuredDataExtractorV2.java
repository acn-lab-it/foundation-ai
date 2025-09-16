package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ChatV2StructuredDataExtractorV2 {

    @Inject
    ChatModel chatModel;

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    LanguageHelper languageHelper;

    @Inject
    ChatV2DateParserToolV2 dateParserTool;

    @Inject
    ChatV2AddressToolV2 addressTool;

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool("Extract structured data from user message for Step 1 (where and when)")
    public Map<String, Object> extractStep1DataV2(String sessionId, String userMessage) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Estrai data e ora dell'incidente separatamente
            Map<String, Object> dateTimeData = extractIncidentDateTimeDetailed(sessionId, userMessage);
            result.putAll(dateTimeData);

            // Estrai e valida indirizzo
            Map<String, Object> addressData = extractAndValidateAddress(sessionId, userMessage);
            result.putAll(addressData);

            // Calcola confidence generale
            double confidence = calculateStep1Confidence(result);
            result.put("confidence", confidence);

            return result;

        } catch (Exception e) {
            result.put("error", "Error extracting step 1 data: " + e.getMessage());
            result.put("confidence", 0.0);
            return result;
        }
    }

    @Tool("Extract structured data from user message for Step 2 (what happened)")
    public Map<String, Object> extractStep2DataV2(String sessionId, String userMessage) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Estrai descrizione incidente
            String damageDescription = extractDamageDescription(sessionId, userMessage);
            if (damageDescription != null && !damageDescription.trim().isEmpty()) {
                result.put("damageDescription", damageDescription);
                result.put("hasDescription", true);
            } else {
                result.put("hasDescription", false);
            }

            // Estrai dettagli specifici del danno
            Map<String, Object> damageDetails = extractDamageDetails(sessionId, userMessage);
            result.putAll(damageDetails);

            // Estrai circostanze
            String circumstances = extractCircumstances(sessionId, userMessage);
            if (circumstances != null && !circumstances.trim().isEmpty()) {
                result.put("circumstances", circumstances);
                result.put("hasCircumstances", true);
            } else {
                result.put("hasCircumstances", false);
            }

            // Calcola confidence generale
            double confidence = calculateStep2Confidence(result);
            result.put("confidence", confidence);

            return result;

        } catch (Exception e) {
            result.put("error", "Error extracting step 2 data: " + e.getMessage());
            result.put("confidence", 0.0);
            return result;
        }
    }

    private Map<String, Object> extractIncidentDateTimeDetailed(String sessionId, String userMessage) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // APPROCCIO INCREMENTALE: aggiorna SOLO quello che l'AI estrae
            // Prova prima a estrarre solo la data
            String dateOnly = dateParserTool.extractDateOnlyV2(sessionId, userMessage);
            if (dateOnly != null) {
                result.put("incidentDate", dateOnly);
                result.put("hasDate", true);
                result.put("dateConfidence", 0.9);
                System.out.println("DEBUG: Date extracted: " + dateOnly);
            }

            // Prova a estrarre solo l'ora
            String timeOnly = dateParserTool.extractTimeOnlyV2(sessionId, userMessage);
            if (timeOnly != null) {
                result.put("incidentTime", timeOnly);
                result.put("hasTime", true);
                result.put("timeConfidence", 0.9);
                System.out.println("DEBUG: Time extracted: " + timeOnly);
            }

            // Se abbiamo sia data che ora, combinali in incidentDate
            if (dateOnly != null && timeOnly != null) {
                result.put("incidentDate", dateOnly + "T" + timeOnly + "Z");
            } else if (dateOnly != null && timeOnly == null) {
                // Solo data - NON aggiungere ora di default
                result.put("incidentDate", dateOnly);
            } else if (dateOnly == null && timeOnly != null) {
                // Solo ora - NON aggiungere data di default
                result.put("incidentDate", timeOnly);
            }

            // Non usare fallback che desume la data
            // Se non abbiamo né data né ora, lasciamo i flag a false

            return result;

        } catch (Exception e) {
            result.put("hasDate", false);
            result.put("hasTime", false);
            result.put("dateConfidence", 0.0);
            result.put("timeConfidence", 0.0);
            return result;
        }
    }

    private String extractIncidentDateTime(String sessionId, String userMessage) {
        try {
            // Prova prima a estrarre solo la data
            String dateOnly = dateParserTool.extractDateOnlyV2(sessionId, userMessage);
            if (dateOnly != null) {
                return dateOnly;
            }

            // Prova a estrarre solo l'ora
            String timeOnly = dateParserTool.extractTimeOnlyV2(sessionId, userMessage);
            if (timeOnly != null) {
                return timeOnly;
            }

            // Fallback al metodo originale per date complete
            String extractedDate = dateParserTool.extractDateV2(userMessage);
            if (extractedDate != null) {
                String normalizedDate = dateParserTool.normalizeV2(sessionId, extractedDate);
                if (normalizedDate != null) {
                    return normalizedDate;
                }
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }


    private Map<String, Object> extractAndValidateAddress(String sessionId, String userMessage) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Estrai parti specifiche dell'indirizzo
            String street = addressTool.extractStreetOnlyV2(sessionId, userMessage);
            String city = addressTool.extractCityOnlyV2(sessionId, userMessage);
            
            // Estrai altre parti dell'indirizzo usando AI
            Map<String, String> addressParts = extractDetailedAddressParts(sessionId, userMessage);
            System.out.println("DEBUG: extractDetailedAddressParts result: " + addressParts);
            
            // APPROCCIO INCREMENTALE: aggiorna SOLO quello che l'AI ha estratto
            // Street
            if (street != null && !street.trim().isEmpty()) {
                result.put("hasStreet", true);
                result.put("street", street);
            }
            
            // City
            if (city != null && !city.trim().isEmpty()) {
                result.put("hasCity", true);
                result.put("city", city);
            }
            
            // House Number
            if (addressParts.containsKey("houseNumber") && !addressParts.get("houseNumber").isEmpty()) {
                result.put("hasHouseNumber", true);
                result.put("houseNumber", addressParts.get("houseNumber"));
            }
            
            // Postal Code
            if (addressParts.containsKey("postalCode") && !addressParts.get("postalCode").isEmpty()) {
                result.put("hasPostalCode", true);
                result.put("postalCode", addressParts.get("postalCode"));
            }
            
            // State
            if (addressParts.containsKey("state") && !addressParts.get("state").isEmpty()) {
                result.put("hasState", true);
                result.put("state", addressParts.get("state"));
            }
            
            // Country (se presente)
            if (addressParts.containsKey("country") && !addressParts.get("country").isEmpty()) {
                result.put("hasCountry", true);
                result.put("country", addressParts.get("country"));
            }
            
            // Address valid solo se abbiamo i campi obbligatori
            boolean hasStreet = result.containsKey("hasStreet") && (Boolean) result.get("hasStreet");
            boolean hasCity = result.containsKey("hasCity") && (Boolean) result.get("hasCity");
            boolean hasHouseNumber = result.containsKey("hasHouseNumber") && (Boolean) result.get("hasHouseNumber");
            result.put("addressValid", hasStreet && hasCity && hasHouseNumber);
            
            System.out.println("DEBUG: INCREMENTAL UPDATE - Only updating what AI found:");
            System.out.println("  hasStreet: " + result.get("hasStreet") + " (street: " + result.get("street") + ")");
            System.out.println("  hasCity: " + result.get("hasCity") + " (city: " + result.get("city") + ")");
            System.out.println("  hasHouseNumber: " + result.get("hasHouseNumber") + " (houseNumber: " + result.get("houseNumber") + ")");
            System.out.println("  hasPostalCode: " + result.get("hasPostalCode") + " (postalCode: " + result.get("postalCode") + ")");
            System.out.println("  hasState: " + result.get("hasState") + " (state: " + result.get("state") + ")");
            
            // L'indirizzo completo sarà costruito dal ChatV2Resource usando buildFinalIncidentLocation
            // Validazione: solo street, houseNumber e city sono obbligatori
            result.put("addressValid", hasStreet && hasCity && hasHouseNumber);

        } catch (Exception e) {
            result.put("hasAddress", false);
            result.put("addressValid", false);
            result.put("error", "Error validating address: " + e.getMessage());
        }

        return result;
    }

    private Map<String, String> extractDetailedAddressParts(String sessionId, String userMessage) {
        Map<String, String> parts = new HashMap<>();
        
        try {
            String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "it";
            
            String systemPrompt = String.format("""
                You are an expert at extracting and inferring address components from text.
                Extract the following components from the user message and return them as JSON:
                
                {
                    "street": "street name without number",
                    "houseNumber": "house/building number", 
                    "city": "city name",
                    "postalCode": "postal code/ZIP code (infer from city if not provided)",
                    "state": "state name (infer from city if not provided, no province/region)"
                }
                
                IMPORTANT RULES:
                1. Extract only fields that are clearly present in the text
                2. Use your knowledge to infer postal code and state from city name when possible
                3. For Italian cities: infer "Italia" as state (e.g., Milano -> Italia, Roma -> Italia), same for other statese, France, Germany, England. etc.
                4. For postal codes: infer common ones for major cities (e.g., Milano -> 20100, Roma -> 00100)
                5. Do NOT include country field
                6. Return null for fields that cannot be determined or inferred
                7. For street: extract ONLY the street name WITHOUT house numbers
                
                Examples:
                - "Via Pacini 16, Milano" -> {"street": "Via Pacini", "houseNumber": "16", "city": "Milano", "postalCode": "20100", "state": "Italia"}
                - "Corso Italia 45, Roma" -> {"street": "Corso Italia", "houseNumber": "45", "city": "Roma", "postalCode": "00100", "state": "Italia"}
                - "Linzer Str. 10, Wien" -> {"street": "Linzer Str.", "houseNumber": "10", "city": "Wien", "postalCode": "1010", "state": "Austria"}
                
                Language: %s
                """, lang);
            
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMessage)
            )).aiMessage().text();
            
            System.out.println("DEBUG: extractDetailedAddressParts AI response: " + response);
            
            // Parse JSON response
            if (response != null && !response.trim().equals("null")) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(response);
                
                if (jsonNode.has("street") && !jsonNode.get("street").isNull()) {
                    parts.put("street", jsonNode.get("street").asText());
                }
                if (jsonNode.has("houseNumber") && !jsonNode.get("houseNumber").isNull()) {
                    parts.put("houseNumber", jsonNode.get("houseNumber").asText());
                }
                if (jsonNode.has("city") && !jsonNode.get("city").isNull()) {
                    parts.put("city", jsonNode.get("city").asText());
                }
                if (jsonNode.has("postalCode") && !jsonNode.get("postalCode").isNull()) {
                    parts.put("postalCode", jsonNode.get("postalCode").asText());
                }
                if (jsonNode.has("state") && !jsonNode.get("state").isNull()) {
                    parts.put("state", jsonNode.get("state").asText());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error extracting detailed address parts: " + e.getMessage());
        }
        
        return parts;
    }

    private String extractAddressFromMessage(String sessionId, String userMessage) {
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        String sys = """
            You are an expert at extracting address information from text.
            Extract the incident location/address from the user message.
            Return ONLY the address as a single string, or null if no address is found.
            Look for patterns like:
            - "in via Roma 123, Milano"
            - "at 123 Main Street, New York"
            - "presso Piazza Duomo, Firenze"
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.address.extractionPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("userMessage", userMessage));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(userMessage)))
                .build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        // Pulisci la risposta
        String address = raw.trim();
        if (address.equalsIgnoreCase("null") || address.isEmpty()) {
            return null;
        }

        return address;
    }

    private String extractDamageDescription(String sessionId, String userMessage) {
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        String sys = """
            You are an expert at extracting incident descriptions from text.
            Extract a clear description of what happened from the user message.
            Return ONLY the description as a single string, or null if no description is found.
            Focus on the actual incident/damage, not location or time.
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.damage.descriptionPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("userMessage", userMessage));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(userMessage)))
                .build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        String description = raw.trim();
        if (description.equalsIgnoreCase("null") || description.isEmpty()) {
            return null;
        }

        return description;
    }

    private Map<String, Object> extractDamageDetails(String sessionId, String userMessage) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

            String sys = """
                You are an expert at analyzing damage details from text.
                Extract specific damage information from the user message.
                Return ONLY a JSON with this structure:
                {
                  "damageType": "string or null",
                  "damageSeverity": "string or null", 
                  "affectedAreas": "string or null",
                  "estimatedCost": "string or null",
                  "hasDetails": true/false
                }
                """;

            try {
                if (languageHelper != null) {
                    LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.damage.detailsPrompt");
                    if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                        sys = languageHelper.applyVariables(p.prompt, Map.of("userMessage", userMessage));
                    }
                }
            } catch (Exception ignore) {
                // usa fallback
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(sys), UserMessage.from(userMessage)))
                    .responseFormat(
                            ResponseFormat.builder()
                                    .type(JSON)
                                    .jsonSchema(
                                            JsonSchema.builder()
                                                    .name("damageDetails")
                                                    .rootElement(
                                                            JsonObjectSchema.builder()
                                                                    .addStringProperty("damageType")
                                                                    .addStringProperty("damageSeverity")
                                                                    .addStringProperty("affectedAreas")
                                                                    .addStringProperty("estimatedCost")
                                                                    .addBooleanProperty("hasDetails")
                                                                    .build())
                                                    .build()
                                    )
                    .build()).build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String raw = chatResponse.aiMessage().text();

            @SuppressWarnings("unchecked")
            Map<String, Object> extracted = mapper.readValue(raw, Map.class);
            result.putAll(extracted);

        } catch (Exception e) {
            result.put("hasDetails", false);
            result.put("error", "Error extracting damage details: " + e.getMessage());
        }

        return result;
    }

    private String extractCircumstances(String sessionId, String userMessage) {
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        String sys = """
            You are an expert at extracting circumstances from incident descriptions.
            Extract the circumstances surrounding the incident from the user message.
            Return ONLY the circumstances as a single string, or null if no circumstances are found.
            Focus on how, why, and under what conditions the incident occurred.
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.circumstances.extractionPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("userMessage", userMessage));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(userMessage)))
                .build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        String circumstances = raw.trim();
        if (circumstances.equalsIgnoreCase("null") || circumstances.isEmpty()) {
            return null;
        }

        return circumstances;
    }

    private double calculateStep1Confidence(Map<String, Object> data) {
        double confidence = 0.0;
        
        if ((Boolean) data.getOrDefault("hasDate", false)) {
            confidence += 0.4;
        }
        if ((Boolean) data.getOrDefault("hasAddress", false)) {
            confidence += 0.3;
        }
        if ((Boolean) data.getOrDefault("addressValid", false)) {
            confidence += 0.3;
        }
        
        return Math.min(confidence, 1.0);
    }

    private double calculateStep2Confidence(Map<String, Object> data) {
        double confidence = 0.0;
        
        if ((Boolean) data.getOrDefault("hasDescription", false)) {
            confidence += 0.4;
        }
        if ((Boolean) data.getOrDefault("hasDetails", false)) {
            confidence += 0.3;
        }
        if ((Boolean) data.getOrDefault("hasCircumstances", false)) {
            confidence += 0.3;
        }
        
        return Math.min(confidence, 1.0);
    }
}
