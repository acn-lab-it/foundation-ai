package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ChatV2CoverageVerifierV2 {

    @Inject
    ChatModel chatModel;

    @Inject
    PolicyRepository policyRepository;

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    LanguageHelper languageHelper;

    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private final ObjectMapper mapper = new ObjectMapper(); // Used for JSON operations in buildContextInfo


    @Tool("Verify policy coverage for specific damage type and date")
    public Map<String, Object> verifyPolicyCoverageV2(String sessionId, String policyNumber, String incidentDate, String damageType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Recupera polizza dal DB
            Optional<Policy> policyOpt = policyRepository.findByPolicyNumber(policyNumber);
            if (!policyOpt.isPresent()) {
                result.put("covered", false);
                result.put("reason", "Polizza non trovata");
                result.put("limitations", List.of("Polizza inesistente"));
                return result;
            }
            
            Policy policy = policyOpt.get();
            
            // Verifica data incidente
            boolean dateCovered = verifyDateCoverage(policy, incidentDate);
            if (!dateCovered) {
                result.put("covered", false);
                result.put("reason", "Data incidente non coperta dalla polizza");
                result.put("limitations", List.of("Data fuori periodo di copertura"));
                return result;
            }

            // Classifica il danno con AI usando informazioni di contesto
            String damageCategory = classifyDamageWithAI(sessionId, damageType);
            result.put("damageCategory", damageCategory);
            
            // Estrai groupName dalla polizza
            String policyGroupName = policy.getProductReference() != null ? 
                policy.getProductReference().getGroupName() : "UNKNOWN";
            result.put("policyGroupName", policyGroupName);
            
            // Applica logica di copertura MOTOR vs MULTIRISK
            boolean isCovered = applyCoverageLogic(damageCategory, policyGroupName);
            result.put("covered", isCovered);
            
            if (!isCovered) {
                // Genera warning multilingua
                String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "it";
                String warningReason = generateWarningReason(lang, damageCategory, policyGroupName);
                result.put("reason", warningReason);
                result.put("limitations", List.of("Tipo di danno non coperto dalla polizza"));
            } else {
                result.put("reason", "Danno coperto dalla polizza");
                result.put("limitations", List.of());
            }

        } catch (Exception e) {
            result.put("covered", false);
            result.put("reason", "Errore durante la verifica: " + e.getMessage());
            result.put("limitations", List.of("Errore di sistema"));
        }

        return result;
    }

    private boolean verifyDateCoverage(Policy policy, String incidentDate) {
        try {
            Date incidentDateObj = parseIncidentDate(incidentDate);
            if (incidentDateObj == null) {
                return false;
            }
            return policy.getBeginDate().before(incidentDateObj) && 
                   policy.getEndDate().after(incidentDateObj);

        } catch (Exception e) {
            return false;
        }
    }

    private Date parseIncidentDate(String incidentDate) {
        try {
            return Date.from(Instant.parse(incidentDate));
        } catch (DateTimeParseException ex) {
            try {
                return Date.from(OffsetDateTime.parse(incidentDate).toInstant());
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String classifyDamageWithAI(String sessionId, String damageType) {
        try {
            String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

            // Recupera tutti i dati disponibili dalla sessione
            ObjectNode sessionData = finalOutputJSONStore.get("final_output", sessionId);
            String contextInfo = buildContextInfo(sessionData, damageType);
            
            // Debug logging
            System.out.println("DEBUG: Damage classification context for session " + sessionId + ":");
            System.out.println(contextInfo);

            String sys = """
                You are an expert insurance damage classifier.
                Classify the given damage information into ONE and ONLY ONE of these categories:
                - MOTOR (vehicle-related damage, car accidents, motorcycle incidents)
                - PROPERTY (building, home, property damage, fire, water, theft)
                - LIABILITY (personal injury, third-party damage, legal liability)
                - OTHER (any other type of damage not fitting the above categories)
                
                Available Information:
                {{contextInfo}}
                
                Return ONLY the category name (MOTOR, PROPERTY, LIABILITY, or OTHER).
                Be precise and choose the most appropriate category based on ALL available information.
                """;

            try {
                if (languageHelper != null) {
                    LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.coverage.damageClassificationPrompt");
                    if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                        sys = p.prompt;
                    }
                }
            } catch (Exception ignore) {
                // usa fallback
            }

            sys = languageHelper.applyVariables(sys, Map.of("contextInfo", contextInfo));
            
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(sys),
                            UserMessage.from("Classifica il tipo di danno in una sola categoria basandoti su tutte le informazioni disponibili.")
                    ))
                    .temperature(0.1) // Low temperature for consistent classification
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String category = chatResponse.aiMessage().text().trim().toUpperCase();
            
            // Validate and normalize the category
            if (category.equals("MOTOR") || category.equals("PROPERTY") || 
                category.equals("LIABILITY") || category.equals("OTHER")) {
                return category;
            } else {
                // Fallback to OTHER if classification is unclear
                return "OTHER";
            }

        } catch (Exception e) {
            System.err.println("Error in damage classification: " + e.getMessage());
            return "OTHER"; // Safe fallback
        }
    }

    private String buildContextInfo(ObjectNode sessionData, String damageType) {
        StringBuilder context = new StringBuilder();
        
        try {
            // Informazioni base
            context.append("User Description: ").append(damageType != null ? damageType : "N/A").append("\n");
            
            if (sessionData != null) {
                // Informazioni dall'incidente
                if (sessionData.has("whatHappenedContext") && !sessionData.get("whatHappenedContext").isNull()) {
                    context.append("Incident Context: ").append(sessionData.get("whatHappenedContext").asText()).append("\n");
                }
                
                if (sessionData.has("whatHappenedCode") && !sessionData.get("whatHappenedCode").isNull()) {
                    context.append("Incident Code: ").append(sessionData.get("whatHappenedCode").asText()).append("\n");
                }
                
                // Dettagli del danno
                if (sessionData.has("damageDetails") && !sessionData.get("damageDetails").isNull()) {
                    context.append("Damage Details: ").append(sessionData.get("damageDetails").asText()).append("\n");
                }
                
                // Circostanze
                if (sessionData.has("circumstances") && !sessionData.get("circumstances").isNull()) {
                    ObjectNode circumstances = (ObjectNode) sessionData.get("circumstances");
                    if (circumstances.has("details") && !circumstances.get("details").isNull()) {
                        context.append("Circumstances Details: ").append(circumstances.get("details").asText()).append("\n");
                    }
                    if (circumstances.has("notes") && !circumstances.get("notes").isNull()) {
                        context.append("Circumstances Notes: ").append(circumstances.get("notes").asText()).append("\n");
                    }
                }
                
                // Analisi media e OCR
                if (sessionData.has("imagesUploaded") && !sessionData.get("imagesUploaded").isNull()) {
                    context.append("Media Analysis:\n");
                    sessionData.get("imagesUploaded").forEach(media -> {
                        if (media.has("mediaDescription") && !media.get("mediaDescription").isNull()) {
                            context.append("  - ").append(media.get("mediaDescription").asText()).append("\n");
                        }
                    });
                }
                
                // Informazioni aggiuntive da _internals se disponibili
                if (sessionData.has("_internals") && !sessionData.get("_internals").isNull()) {
                    ObjectNode internals = (ObjectNode) sessionData.get("_internals");
                    
                    // OCR results se disponibili
                    if (internals.has("ocrResults") && !internals.get("ocrResults").isNull()) {
                        context.append("OCR Analysis:\n");
                        internals.get("ocrResults").forEach(ocr -> {
                            if (ocr.has("damagedEntity") && !ocr.get("damagedEntity").isNull()) {
                                context.append("  - Damaged Entity: ").append(ocr.get("damagedEntity").asText());
                            }
                            if (ocr.has("eventType") && !ocr.get("eventType").isNull()) {
                                context.append(" (Event: ").append(ocr.get("eventType").asText()).append(")");
                            }
                            if (ocr.has("confidence") && !ocr.get("confidence").isNull()) {
                                context.append(" [Confidence: ").append(ocr.get("confidence").asDouble()).append("]");
                            }
                            context.append("\n");
                        });
                    }
                    
                    // Media analysis se disponibile
                    if (internals.has("mediaAnalysis") && !internals.get("mediaAnalysis").isNull()) {
                        context.append("Media Analysis Summary: ").append(internals.get("mediaAnalysis").asText()).append("\n");
                    }
                }
            }
            
            // Se non abbiamo informazioni sufficienti
            if (context.length() <= 20) {
                context.append("Limited information available for classification.");
            }
            
        } catch (Exception e) {
            System.err.println("Error building context info: " + e.getMessage());
            context.append("Error retrieving context information.");
        }
        
        return context.toString();
    }

    private boolean applyCoverageLogic(String damageCategory, String policyGroupName) {
        // Logica di copertura:
        // - MOTOR policy: copre solo danni MOTOR
        // - MULTIRISK policy: copre tutto tranne MOTOR
        
        if (damageCategory == null || policyGroupName == null) {
            return false;
        }
        
        if (damageCategory.equals("MOTOR") && policyGroupName.equals("MOTOR")) {
            return true;
        } else if (!damageCategory.equals("MOTOR") && policyGroupName.equals("MULTIRISK")) {
            return true;
        }
        
        return false;
    }

    private String generateWarningReason(String lang, String damageCategory, String policyGroupName) {
        String normalizedLang = normalizeLanguage(lang);
        
        if ("it".equals(normalizedLang)) {
            if (damageCategory.equals("MOTOR") && policyGroupName.equals("MULTIRISK")) {
                return "Il danno MOTOR non è coperto da una polizza MULTIRISK. È necessaria una polizza MOTOR.";
            } else if (!damageCategory.equals("MOTOR") && policyGroupName.equals("MOTOR")) {
                return "Il danno " + damageCategory + " non è coperto da una polizza MOTOR. È necessaria una polizza MULTIRISK.";
            } else {
                return "Tipo di danno non coperto dalla polizza " + policyGroupName;
            }
        } else if ("de".equals(normalizedLang)) {
            if (damageCategory.equals("MOTOR") && policyGroupName.equals("MULTIRISK")) {
                return "MOTOR-Schäden sind nicht durch eine MULTIRISK-Police abgedeckt. Eine MOTOR-Police ist erforderlich.";
            } else if (!damageCategory.equals("MOTOR") && policyGroupName.equals("MOTOR")) {
                return "Schäden vom Typ " + damageCategory + " sind nicht durch eine MOTOR-Police abgedeckt. Eine MULTIRISK-Police ist erforderlich.";
            } else {
                return "Schadenstyp nicht durch " + policyGroupName + "-Police abgedeckt";
            }
        } else {
            // English default
            if (damageCategory.equals("MOTOR") && policyGroupName.equals("MULTIRISK")) {
                return "MOTOR damage is not covered by a MULTIRISK policy. A MOTOR policy is required.";
            } else if (!damageCategory.equals("MOTOR") && policyGroupName.equals("MOTOR")) {
                return damageCategory + " damage is not covered by a MOTOR policy. A MULTIRISK policy is required.";
            } else {
                return "Damage type not covered by " + policyGroupName + " policy";
            }
        }
    }

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en"; // Default English
        }
        
        String lowerLang = lang.toLowerCase().trim();
        
        if (lowerLang.startsWith("it") || lowerLang.contains("italian")) {
            return "it";
        } else if (lowerLang.startsWith("de") || lowerLang.contains("german")) {
            return "de";
        } else {
            return "en"; // Default to English
        }
    }


    @Tool("Check policy active status")
    public boolean isPolicyActiveV2(String policyNumber) {
        try {
            return policyRepository.findByPolicyNumber(policyNumber)
                    .map(policy -> "ACTIVE".equalsIgnoreCase(policy.getPolicyStatus()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    @Tool("Get policy coverage period")
    public Map<String, Object> getPolicyCoveragePeriodV2(String policyNumber) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            return policyRepository.findByPolicyNumber(policyNumber)
                    .map(policy -> {
                        result.put("beginDate", policy.getBeginDate().toString());
                        result.put("endDate", policy.getEndDate().toString());
                        result.put("isActive", "ACTIVE".equalsIgnoreCase(policy.getPolicyStatus()));
                        return result;
                    })
                    .orElse(Map.of("error", "Polizza non trovata"));

        } catch (Exception e) {
            result.put("error", "Errore nel recupero periodo copertura: " + e.getMessage());
            return result;
        }
    }
}
