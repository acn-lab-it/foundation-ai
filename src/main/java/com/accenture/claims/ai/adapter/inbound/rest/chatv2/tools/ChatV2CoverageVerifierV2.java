package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool("Verify policy coverage for specific damage type and date")
    public Map<String, Object> verifyPolicyCoverageV2(String sessionId, String policyNumber, String incidentDate, String damageType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Verifica esistenza polizza
            if (!policyRepository.findByPolicyNumber(policyNumber).isPresent()) {
                result.put("covered", false);
                result.put("reason", "Polizza non trovata");
                result.put("limitations", List.of("Polizza inesistente"));
                return result;
            }

            // Verifica data incidente
            boolean dateCovered = verifyDateCoverage(policyNumber, incidentDate);
            if (!dateCovered) {
                result.put("covered", false);
                result.put("reason", "Data incidente non coperta dalla polizza");
                result.put("limitations", List.of("Data fuori periodo di copertura"));
                return result;
            }

            // Verifica tipo di danno
            boolean damageTypeCovered = verifyDamageTypeCoverage(policyNumber, damageType, sessionId);
            if (!damageTypeCovered) {
                result.put("covered", false);
                result.put("reason", "Tipo di danno non coperto dalla polizza");
                result.put("limitations", List.of("Danno non coperto dalla polizza"));
                return result;
            }

            // Verifica copertura completa
            Map<String, Object> fullCoverage = verifyFullCoverage(policyNumber, incidentDate, damageType, sessionId);
            result.putAll(fullCoverage);

        } catch (Exception e) {
            result.put("covered", false);
            result.put("reason", "Errore durante la verifica: " + e.getMessage());
            result.put("limitations", List.of("Errore di sistema"));
        }

        return result;
    }

    @Tool("Check if damage type is covered by policy")
    public boolean isDamageTypeCoveredV2(String policyNumber, String whatHappenedCode, String damageDetails) {
        try {
            // Verifica base: se abbiamo un codice valido
            if (whatHappenedCode == null || whatHappenedCode.equals("UNKNOWN")) {
                return false;
            }

            // Verifica con AI se il tipo di danno è coperto
            return verifyDamageTypeWithAI(policyNumber, whatHappenedCode, damageDetails);

        } catch (Exception e) {
            return false;
        }
    }

    @Tool("Get policy coverage details")
    public Map<String, Object> getPolicyCoverageDetailsV2(String policyNumber) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            return policyRepository.findByPolicyNumber(policyNumber)
                    .map(policy -> {
                        result.put("policyNumber", policy.getPolicyNumber());
                        result.put("beginDate", policy.getBeginDate().toString());
                        result.put("endDate", policy.getEndDate().toString());
                        result.put("status", policy.getPolicyStatus());
                        result.put("coverageType", policy.getProductReference() != null ? 
                                policy.getProductReference().getCode() : "UNKNOWN");
                        return result;
                    })
                    .orElse(Map.of("error", "Polizza non trovata"));

        } catch (Exception e) {
            result.put("error", "Errore nel recupero dettagli polizza: " + e.getMessage());
            return result;
        }
    }

    private boolean verifyDateCoverage(String policyNumber, String incidentDate) {
        try {
            return policyRepository.findByPolicyNumber(policyNumber)
                    .map(policy -> {
                        Date incidentDateObj = parseIncidentDate(incidentDate);
                        if (incidentDateObj == null) {
                            return false;
                        }
                        return policy.getBeginDate().before(incidentDateObj) && 
                               policy.getEndDate().after(incidentDateObj);
                    })
                    .orElse(false);

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

    private boolean verifyDamageTypeCoverage(String policyNumber, String damageType, String sessionId) {
        try {
            // Verifica base: se non abbiamo tipo di danno, assumiamo coperto
            if (damageType == null || damageType.trim().isEmpty()) {
                return true;
            }

            // Verifica con AI
            return verifyDamageTypeWithAI(policyNumber, damageType, "");

        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyDamageTypeWithAI(String policyNumber, String damageType, String damageDetails) {
        try {
            String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(policyNumber) : "en";

            String sys = """
                You are an expert insurance coverage analyzer.
                Determine if the given damage type is covered by the policy.
                
                Policy Number: {{policyNumber}}
                Damage Type: {{damageType}}
                Damage Details: {{damageDetails}}
                
                Return ONLY a JSON with this structure:
                {
                  "covered": true/false,
                  "reason": "explanation",
                  "coverageType": "PROPERTY|MOTOR|LIABILITY|UNKNOWN",
                  "confidence": 0.0-1.0
                }
                """;

            try {
                if (languageHelper != null) {
                    LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.coverage.verificationPrompt");
                    if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                        sys = p.prompt;
                    }
                }
            } catch (Exception ignore) {
                // usa fallback
            }

            sys = languageHelper.applyVariables(sys, Map.of(
                    "policyNumber", policyNumber,
                    "damageType", damageType,
                    "damageDetails", damageDetails
            ));
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(sys),
                            SystemMessage.from("""
                                    Ritorna un boolean: true se coerente con la polizza (MULTIRISK copre tutto tranne MOTOR), altrimenti false.
                                    
                                    """),
                            UserMessage.from("Verifica se il danno è coperto dalla polizza.")
                    ))
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String raw = chatResponse.aiMessage().text();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = mapper.readValue(raw, Map.class);
            return (Boolean) result.getOrDefault("covered", false);

        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> verifyFullCoverage(String policyNumber, String incidentDate, String damageType, String sessionId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

            String sys = """
                You are an expert insurance coverage analyzer.
                Perform a comprehensive coverage verification for the given policy and incident.
                
                Policy Number: {{policyNumber}}
                Incident Date: {{incidentDate}}
                Damage Type: {{damageType}}
                
                Return ONLY a JSON with this structure:
                {
                  "covered": true/false,
                  "reason": "detailed explanation",
                  "coverageType": "PROPERTY|MOTOR|LIABILITY|UNKNOWN",
                  "limitations": ["limitation1", "limitation2"],
                  "exclusions": ["exclusion1", "exclusion2"],
                  "confidence": 0.0-1.0,
                  "recommendations": ["recommendation1", "recommendation2"]
                }
                """;

            try {
                if (languageHelper != null) {
                    LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.coverage.fullVerificationPrompt");
                    if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                        sys = p.prompt;
                    }
                }
            } catch (Exception ignore) {
                // usa fallback
            }

            sys = languageHelper.applyVariables(sys, Map.of(
                    "policyNumber", policyNumber,
                    "incidentDate", incidentDate,
                    "damageType", damageType
            ));

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(sys),
                            UserMessage.from("Esegui verifica completa della copertura.")
                    ))
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            String raw = chatResponse.aiMessage().text();

            @SuppressWarnings("unchecked")
            Map<String, Object> verification = mapper.readValue(raw, Map.class);
            result.putAll(verification);

        } catch (Exception e) {
            result.put("covered", false);
            result.put("reason", "Errore nella verifica completa: " + e.getMessage());
            result.put("confidence", 0.0);
        }

        return result;
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
