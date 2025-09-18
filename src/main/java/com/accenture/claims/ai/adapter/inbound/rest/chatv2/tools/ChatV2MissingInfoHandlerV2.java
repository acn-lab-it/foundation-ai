package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ChatV2MissingInfoHandlerV2 {

    @Inject
    ChatModel chatModel;

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    LanguageHelper languageHelper;

    @Tool("Check if all required data is present for current step")
    public Map<String, Object> validateStepDataV2(String sessionId, String currentStep, Map<String, Object> extractedData) {
        Map<String, Object> result = new HashMap<>();
        List<String> missingFields = new ArrayList<>();
        Map<String, Double> confidence = new HashMap<>();

        if ("step1".equals(currentStep)) {
            validateStep1Data(extractedData, missingFields, confidence);
        } else if ("step2".equals(currentStep)) {
            validateStep2Data(extractedData, missingFields, confidence);
        }

        result.put("complete", missingFields.isEmpty());
        result.put("missingFields", missingFields);
        result.put("confidence", confidence);
        result.put("step", currentStep);

        return result;
    }

    @Tool("Analyze missing information and generate specific requests")
    public String generateMissingInfoRequestV2(String sessionId, String currentStep, Map<String, Object> extractedData, String acceptLanguage) {
        System.out.println("=== DEBUG generateMissingInfoRequestV2 START ===");
        System.out.println("SessionId: " + sessionId);
        System.out.println("CurrentStep: " + currentStep);
        System.out.println("ExtractedData: " + extractedData);
        System.out.println("AcceptLanguage: " + acceptLanguage);
        
        try {
            Map<String, Object> validation = validateStepDataV2(sessionId, currentStep, extractedData);
            @SuppressWarnings("unchecked")
            List<String> missingFields = (List<String>) validation.get("missingFields");
            
            System.out.println("DEBUG: Missing fields: " + missingFields);

            if (missingFields.isEmpty()) {
                return "Tutte le informazioni necessarie sono state fornite.";
            }

            // Usa direttamente acceptLanguage, con fallback a sessionLanguageContext
            String lang = acceptLanguage;
            if (lang == null || lang.isBlank()) {
                lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "it";
            }
            return generateSpecificRequest(sessionId, currentStep, missingFields, extractedData, lang);

        } catch (Exception e) {
            return "Errore nell'analisi delle informazioni mancanti: " + e.getMessage();
        }
    }

    private void validateStep1Data(Map<String, Object> data, List<String> missingFields, Map<String, Double> confidence) {
        // Verifica data e ora separatamente
        boolean hasDate = (Boolean) data.getOrDefault("hasDate", false);
        boolean hasTime = (Boolean) data.getOrDefault("hasTime", false);
        
        if (!hasDate && !hasTime) {
            missingFields.add("data e ora dell'incidente");
        } else if (!hasDate && hasTime) {
            String timeInfo = (String) data.getOrDefault("incidentTime", "");
            missingFields.add("data dell'incidente (ho registrato l'orario " + timeInfo + ")");
        } else if (hasDate && !hasTime) {
            String dateInfo = (String) data.getOrDefault("incidentDate", "");
            missingFields.add("ora dell'incidente (ho registrato la data " + dateInfo + ")");
        }
        
        confidence.put("date", (Double) data.getOrDefault("dateConfidence", 0.0));
        confidence.put("time", (Double) data.getOrDefault("timeConfidence", 0.0));

        // Verifica indirizzo separatamente per parti obbligatorie
        boolean hasStreet = (Boolean) data.getOrDefault("hasStreet", false);
        boolean hasCity = (Boolean) data.getOrDefault("hasCity", false);
        boolean hasHouseNumber = (Boolean) data.getOrDefault("hasHouseNumber", false);
        
        // Parti opzionali (inferite dall'AI)
        boolean hasPostalCode = (Boolean) data.getOrDefault("hasPostalCode", false);
        boolean hasState = (Boolean) data.getOrDefault("hasState", false);
        
        // Costruisci messaggio specifico per parti mancanti OBBLIGATORIE
        List<String> missingAddressParts = new ArrayList<>();
        List<String> existingAddressParts = new ArrayList<>();
        
        if (!hasStreet) {
            missingAddressParts.add("via");
        } else {
            existingAddressParts.add("via " + data.getOrDefault("street", ""));
        }
        
        if (!hasHouseNumber) {
            missingAddressParts.add("numero civico");
        } else {
            existingAddressParts.add("civico " + data.getOrDefault("houseNumber", ""));
        }
        
        if (!hasCity) {
            missingAddressParts.add("città");
        } else {
            existingAddressParts.add("città " + data.getOrDefault("city", ""));
        }
        
        // Aggiungi info opzionali se disponibili (per mostrare cosa abbiamo inferito)
        if (hasPostalCode) {
            existingAddressParts.add("CAP " + data.getOrDefault("postalCode", ""));
        }
        if (hasState) {
            existingAddressParts.add("stato " + data.getOrDefault("state", ""));
        }
        
        // Genera messaggio specifico solo per parti obbligatorie
        if (missingAddressParts.isEmpty()) {
            // Indirizzo completo (almeno le parti obbligatorie)
        } else if (existingAddressParts.isEmpty()) {
            missingFields.add("indirizzo completo dell'incidente (via, numero civico e città)");
        } else {
            String existingInfo = String.join(", ", existingAddressParts);
            String missingInfo = String.join(", ", missingAddressParts);
            missingFields.add(missingInfo + " (ho registrato: " + existingInfo + ")");
        }
        
        confidence.put("address", calculateAddressConfidence(data));
    }

    private void validateStep2Data(Map<String, Object> data, List<String> missingFields, Map<String, Double> confidence) {
        // Verifica campi obbligatori per step 2 considerando NONE/UNKNOWN come null
        Object whatHappenedCode = data.get("whatHappenedCode");
        Object whatHappenedContext = data.get("whatHappenedContext");
        Object circumstances = data.get("circumstances");
        Object damageDetails = data.get("damageDetails");
        
        boolean hasIncidentType = !isNullish(whatHappenedCode);
        boolean hasIncidentContext = !isNullish(whatHappenedContext);
        boolean hasCircumstances = !isNullishCircumstances(circumstances);
        boolean hasDamageDetails = !isNullish(damageDetails);

        // Log detailed validation info
        System.out.println("DEBUG: Step2 validation details:");
        System.out.println("  - whatHappenedCode: " + whatHappenedCode + " (nullish: " + !hasIncidentType + ")");
        System.out.println("  - whatHappenedContext: " + whatHappenedContext + " (nullish: " + !hasIncidentContext + ")");
        System.out.println("  - circumstances: " + circumstances + " (nullish: " + !hasCircumstances + ")");
        System.out.println("  - damageDetails: " + damageDetails + " (nullish: " + !hasDamageDetails + ")");

        if (!hasIncidentType) {
            missingFields.add("tipo di incidente");
        }
        if (!hasIncidentContext) {
            missingFields.add("contesto incidente");
        }
        if (!hasCircumstances) {
            missingFields.add("circostanze dell'incidente");
        }
        if (!hasDamageDetails) {
            missingFields.add("dettagli del danno");
        }

        confidence.put("incidentType", hasIncidentType ? 1.0 : 0.0);
        confidence.put("incidentContext", hasIncidentContext ? 1.0 : 0.0);
        confidence.put("circumstances", hasCircumstances ? 1.0 : 0.0);
        confidence.put("damageDetails", hasDamageDetails ? 1.0 : 0.0);

        System.out.println("DEBUG: Step2 validation - hasIncidentType: " + hasIncidentType +
                ", hasIncidentContext: " + hasIncidentContext +
                ", hasCircumstances: " + hasCircumstances +
                ", hasDamageDetails: " + hasDamageDetails);
        System.out.println("DEBUG: Missing fields: " + missingFields);

    }

    private boolean isNullish(Object v) {
        if (v == null) return true;
        if (v instanceof CharSequence) {
            String s = v.toString().trim();
            if (s.isEmpty()) return true;
            String up = s.toUpperCase(java.util.Locale.ROOT);
            
            // Check for basic null values
            if ("NULL".equals(up) || "NONE".equals(up) || "UNKNOWN".equals(up)) {
                return true;
            }
            
            // Check for patterns like "NONE - NONE (conf. 1,00)" or "UNKNOWN - UNKNOWN (conf. 0.95)"
            // This regex matches: WORD - WORD (conf. NUMBER) where WORD is NONE, UNKNOWN, or similar
            if (up.matches("^(NONE|UNKNOWN|NULL)\\s*-\\s*(NONE|UNKNOWN|NULL)\\s*\\(conf\\.\\s*[0-9.,]+\\s*\\)$")) {
                System.out.println("DEBUG: Detected nullish pattern with confidence: " + s);
                return true;
            }
            
            // Check for patterns like "NONE - NONE" (without confidence)
            if (up.matches("^(NONE|UNKNOWN|NULL)\\s*-\\s*(NONE|UNKNOWN|NULL)$")) {
                System.out.println("DEBUG: Detected nullish pattern without confidence: " + s);
                return true;
            }
            
            // Check for patterns like "NONE (conf. 1,00)" - single value with confidence
            if (up.matches("^(NONE|UNKNOWN|NULL)\\s*\\(conf\\.\\s*[0-9.,]+\\s*\\)$")) {
                System.out.println("DEBUG: Detected single nullish value with confidence: " + s);
                return true;
            }
        }
        return false;
    }

    private boolean isNullishCircumstances(Object v) {
        if (isNullish(v)) return true;
        if (v instanceof Map map) {
            Object details = map.get("details");
            Object notes = map.get("notes");
            // Considera assenti se entrambi vuoti/none/unknown
            boolean detailsNullish = isNullish(details);
            boolean notesNullish = isNullish(notes);
            
            System.out.println("DEBUG: isNullishCircumstances - details: " + details + " (nullish: " + detailsNullish + ")");
            System.out.println("DEBUG: isNullishCircumstances - notes: " + notes + " (nullish: " + notesNullish + ")");
            
            return detailsNullish && notesNullish;
        }
        return false;
    }

    private double calculateAddressConfidence(Map<String, Object> data) {
        double confidence = 0.0;
        
        // Pesi per le diverse parti dell'indirizzo
        // Parti obbligatorie (peso maggiore)
        if ((Boolean) data.getOrDefault("hasStreet", false)) {
            confidence += 0.4;
        }
        if ((Boolean) data.getOrDefault("hasHouseNumber", false)) {
            confidence += 0.3;
        }
        if ((Boolean) data.getOrDefault("hasCity", false)) {
            confidence += 0.3;
        }
        
        // Parti opzionali (peso minore, bonus se presenti)
        if ((Boolean) data.getOrDefault("hasPostalCode", false)) {
            confidence += 0.1;
        }
        if ((Boolean) data.getOrDefault("hasState", false)) {
            confidence += 0.1;
        }
        
        return Math.min(confidence, 1.0);
    }

    @SuppressWarnings("unused")
    private double calculateDetailsConfidence(Map<String, Object> data) {
        double confidence = 0.0;
        
        if ((Boolean) data.getOrDefault("hasDetails", false)) {
            confidence += 0.4;
        }
        if (data.containsKey("damageType") && data.get("damageType") != null) {
            confidence += 0.3;
        }
        if (data.containsKey("damageSeverity") && data.get("damageSeverity") != null) {
            confidence += 0.3;
        }
        
        return Math.min(confidence, 1.0);
    }

    @SuppressWarnings("unused")
    private double calculateCircumstancesConfidence(Map<String, Object> data) {
        double confidence = 0.0;
        
        if ((Boolean) data.getOrDefault("hasCircumstances", false)) {
            confidence += 0.6;
        }
        if (data.containsKey("circumstances") && data.get("circumstances") != null) {
            String circumstances = (String) data.get("circumstances");
            if (circumstances.length() > 20) { // Circostanze dettagliate
                confidence += 0.4;
            }
        }
        
        return Math.min(confidence, 1.0);
    }

    private String generateSpecificRequest(String sessionId, String currentStep, List<String> missingFields, Map<String, Object> extractedData, String lang) {
        // Usa AI per generare messaggi naturali di richiesta informazioni mancanti
        return generateAIRequest(sessionId, currentStep, missingFields, extractedData, lang);
    }

    private String generateAIRequest(String sessionId, String currentStep, List<String> missingFields, Map<String, Object> extractedData, String lang) {
        if (missingFields.isEmpty()) {
            return "Tutte le informazioni necessarie sono state fornite.";
        }

        // Debug: stampa la lingua ricevuta
        System.out.println("=== LANGUAGE DEBUG ===");
        System.out.println("Received lang: '" + lang + "'");
        
        // Normalizza la lingua
        String normalizedLang = normalizeLanguage(lang);
        System.out.println("Normalized lang: '" + normalizedLang + "'");
        System.out.println("=====================");
        
        // Crea un prompt per l'AI basato sulla lingua e sui campi mancanti
        String systemPrompt = createSystemPrompt(normalizedLang, currentStep, missingFields, extractedData);
        
        try {
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("Genera un messaggio per richiedere le informazioni mancanti.")
            )).aiMessage().text();
            
            return response != null ? response.trim() : generateFallbackRequest(currentStep, missingFields, normalizedLang);
        } catch (Exception e) {
            // Fallback ai messaggi programmatici se l'AI fallisce
            return generateFallbackRequest(currentStep, missingFields, normalizedLang);
        }
    }

    private String createSystemPrompt(String lang, String currentStep, List<String> missingFields, Map<String, Object> extractedData) {
        StringBuilder prompt = new StringBuilder();

        if ("it".equals(lang)) {
            prompt.append("Sei un assistente di una compagnia assicurativa in una chat. ");
            prompt.append("Devi richiedere informazioni mancanti per la gestione di un sinistro. ");
            prompt.append("Sii cortese, professionale e diretto. ");
            prompt.append("NON usare firme email, saluti formali o chiusure come 'Cordiali saluti'. ");
            prompt.append("Il messaggio deve essere strutturato in due sezioni: cosa abbiamo già e cosa ci manca ancora.\n\n");

            prompt.append("INFORMAZIONI GIÀ RACCOLTE:\n");
            boolean hasAnyInfo = false;
            
            // Debug logging for prompt generation
            System.out.println("DEBUG: Creating prompt - checking extractedData:");
            System.out.println("  - incidentDate: " + extractedData.get("incidentDate") + " (nullish: " + isNullish(extractedData.get("incidentDate")) + ")");
            System.out.println("  - incidentLocation: " + extractedData.get("incidentLocation") + " (nullish: " + isNullish(extractedData.get("incidentLocation")) + ")");
            System.out.println("  - whatHappenedCode: " + extractedData.get("whatHappenedCode") + " (nullish: " + isNullish(extractedData.get("whatHappenedCode")) + ")");
            
            if (extractedData.containsKey("incidentDate") && !isNullish(extractedData.get("incidentDate"))) {
                prompt.append("✓ Data/ora incidente: ").append(extractedData.get("incidentDate")).append("\n");
                hasAnyInfo = true;
            }
            if (extractedData.containsKey("incidentLocation") && !isNullish(extractedData.get("incidentLocation"))) {
                prompt.append("✓ Luogo incidente: ").append(extractedData.get("incidentLocation")).append("\n");
                hasAnyInfo = true;
            }
            if (extractedData.containsKey("whatHappenedCode") && !isNullish(extractedData.get("whatHappenedCode"))) {
                prompt.append("✓ Tipo di incidente: ").append(extractedData.get("whatHappenedCode")).append("\n");
                hasAnyInfo = true;
            }
            if (!hasAnyInfo) {
                prompt.append("Nessuna informazione raccolta finora.\n");
            }

            prompt.append("\nINFORMAZIONI MANCANTI:\n");
            for (String field : missingFields) {
                prompt.append("✗ ").append(field).append("\n");
            }

            prompt.append("\nGenera un messaggio di chat che mostri chiaramente cosa abbiamo e cosa ci manca. ");
            prompt.append("IMPORTANTE: Se mostri date/ore, formattale in modo leggibile per l'utente italiano (es. '15 marzo 2024 alle 14:30' invece di '2024-03-15T14:30:00Z'). ");
            prompt.append("Usa un formato come:\n");
            prompt.append("'Ho già raccolto:\n• [lista di quello che abbiamo]\n\nPer procedere mi servono ancora:\n• [lista di quello che manca]'\n\n");

            // Personalizza il messaggio in base allo step
            if ("step1".equals(currentStep)) {
                prompt.append("Esempio per step1: 'Ho già raccolto:\n• Data dell'incidente: 15 marzo 2024\n\nPer procedere con la segnalazione mi servono ancora:\n• Indirizzo completo dell'incidente'");
            } else if ("step2".equals(currentStep)) {
                prompt.append("Esempio per step2: 'Ho già raccolto:\n• Data e luogo dell'incidente\n• Tipo di incidente: incendio\n\nPer continuare con la gestione del sinistro mi servono ancora:\n• Descrizione dettagliata dell'incidente\n• Dettagli del danno subito'");
            } else {
                prompt.append("Esempio generico: 'Ho già raccolto:\n• [info disponibili]\n\nPer completare la segnalazione mi servono ancora:\n• [info mancanti]'");
            }

        } else if ("de".equals(lang)) {
            prompt.append("Du bist ein Assistent einer Versicherungsgesellschaft in einem Chat. ");
            prompt.append("Du musst fehlende Informationen für die Schadensabwicklung anfordern. ");
            prompt.append("Sei höflich, professionell und direkt. ");
            prompt.append("VERWENDE KEINE E-Mail-Vorlagen, formelle Grüße oder Schlussformeln wie 'Mit freundlichen Grüßen'. ");
            prompt.append("Die Nachricht muss in zwei Abschnitte strukturiert sein: was wir bereits haben und was uns noch fehlt.\n\n");

            prompt.append("BEREITS GESAMMELTE INFORMATIONEN:\n");
            boolean hasAnyInfo = false;
            if (extractedData.containsKey("incidentDate") && !isNullish(extractedData.get("incidentDate"))) {
                prompt.append("✓ Schadensdatum/-zeit: ").append(extractedData.get("incidentDate")).append("\n");
                hasAnyInfo = true;
            }
            if (extractedData.containsKey("incidentLocation") && !isNullish(extractedData.get("incidentLocation"))) {
                prompt.append("✓ Schadensort: ").append(extractedData.get("incidentLocation")).append("\n");
                hasAnyInfo = true;
            }
            if (extractedData.containsKey("whatHappenedCode") && !isNullish(extractedData.get("whatHappenedCode"))) {
                prompt.append("✓ Schadenstyp: ").append(extractedData.get("whatHappenedCode")).append("\n");
                hasAnyInfo = true;
            }
            if (!hasAnyInfo) {
                prompt.append("Bisher keine Informationen gesammelt.\n");
            }

            prompt.append("\nFEHLENDE INFORMATIONEN:\n");
            for (String field : missingFields) {
                prompt.append("✗ ").append(field).append("\n");
            }

            prompt.append("\nGeneriere eine Chat-Nachricht, die klar zeigt, was wir haben und was uns fehlt. ");
            prompt.append("WICHTIG: Wenn du Daten/Zeiten zeigst, formatiere sie lesbar für deutsche Benutzer (z.B. '15. März 2024 um 14:30' statt '2024-03-15T14:30:00Z'). ");
            prompt.append("Verwende ein Format wie:\n");
            prompt.append("'Ich habe bereits gesammelt:\n• [Liste dessen, was wir haben]\n\nUm fortzufahren benötige ich noch:\n• [Liste dessen, was fehlt]'\n\n");

            // Personalizza il messaggio in base allo step
            if ("step1".equals(currentStep)) {
                prompt.append("Beispiel für step1: 'Ich habe bereits gesammelt:\n• Schadensdatum: 15. März 2024\n\nUm mit der Schadensmeldung fortzufahren benötige ich noch:\n• Vollständige Adresse des Schadens'");
            } else if ("step2".equals(currentStep)) {
                prompt.append("Beispiel für step2: 'Ich habe bereits gesammelt:\n• Datum und Ort des Schadens\n• Schadenstyp: Brand\n\nUm mit der Schadensabwicklung fortzufahren benötige ich noch:\n• Detaillierte Schadensbeschreibung\n• Schadensdetails'");
            } else {
                prompt.append("Allgemeines Beispiel: 'Ich habe bereits gesammelt:\n• [verfügbare Infos]\n\nUm die Meldung abzuschließen benötige ich noch:\n• [fehlende Infos]'");
            }

        } else {
            prompt.append("You are an assistant for an insurance company in a chat. ");
            prompt.append("You need to request missing information for claim processing. ");
            prompt.append("Be polite, professional and direct. ");
            prompt.append("DON'T use email templates, formal greetings or closings like 'Best regards'. ");
            prompt.append("The message must be structured in two sections: what we already have and what we still need.\n\n");

            prompt.append("ALREADY COLLECTED INFORMATION:\n");
            boolean hasAnyInfo = false;
            if (extractedData.containsKey("incidentDate") && !isNullish(extractedData.get("incidentDate"))) {
                prompt.append("✓ Incident date/time: ").append(extractedData.get("incidentDate")).append("\n");
                hasAnyInfo = true;
            }
            if (extractedData.containsKey("incidentLocation") && !isNullish(extractedData.get("incidentLocation"))) {
                prompt.append("✓ Incident location: ").append(extractedData.get("incidentLocation")).append("\n");
                hasAnyInfo = true;
            }
            if (extractedData.containsKey("whatHappenedCode") && !isNullish(extractedData.get("whatHappenedCode"))) {
                prompt.append("✓ Incident type: ").append(extractedData.get("whatHappenedCode")).append("\n");
                hasAnyInfo = true;
            }
            if (!hasAnyInfo) {
                prompt.append("No information collected yet.\n");
            }

            prompt.append("\nMISSING INFORMATION:\n");
            for (String field : missingFields) {
                prompt.append("✗ ").append(field).append("\n");
            }

            prompt.append("\nGenerate a chat message that clearly shows what we have and what we're missing. ");
            prompt.append("IMPORTANT: If you show dates/times, format them in a readable way for English users (e.g. 'March 15, 2024 at 2:30 PM' instead of '2024-03-15T14:30:00Z'). ");
            prompt.append("Use a format like:\n");
            prompt.append("'I have already collected:\n• [list of what we have]\n\nTo proceed I still need:\n• [list of what's missing]'\n\n");

            // Personalizza il messaggio in base allo step
            if ("step1".equals(currentStep)) {
                prompt.append("Example for step1: 'I have already collected:\n• Incident date: March 15, 2024\n\nTo proceed with the claim I still need:\n• Complete incident address'");
            } else if ("step2".equals(currentStep)) {
                prompt.append("Example for step2: 'I have already collected:\n• Date and location of the incident\n• Incident type: fire\n\nTo continue with claim processing I still need:\n• Detailed incident description\n• Damage details'");
            } else {
                prompt.append("Generic example: 'I have already collected:\n• [available info]\n\nTo complete the claim I still need:\n• [missing info]'");
            }
        }

        prompt.append("\n\n");
        prompt.append("If the missing information is 'tipo di incidente' then you have to ask for more information about the incident context.");
        prompt.append("Do not say 'tipo di incidente' in the answer, refer to it as 'incident context'.");

        return prompt.toString();
    }

    private String generateFallbackRequest(String currentStep, List<String> missingFields, String lang) {
        // Fallback ai metodi programmatici se l'AI fallisce
        return generateSimpleRequest(currentStep, missingFields, lang);
    }

    private String generateSimpleRequest(String currentStep, List<String> missingFields, String lang) {
        if (missingFields.isEmpty()) {
            return "Tutte le informazioni necessarie sono state fornite.";
        }

        // Normalizza la lingua
        String normalizedLang = normalizeLanguage(lang);

        // Messaggi semplici e diretti in base alla lingua
        if ("it".equals(normalizedLang)) {
            return generateItalianRequest(currentStep, missingFields);
        } else if ("de".equals(normalizedLang)) {
            return generateGermanRequest(currentStep, missingFields);
        } else {
            return generateEnglishRequest(currentStep, missingFields);
        }
    }

    private String normalizeLanguage(String lang) {
        System.out.println("normalizeLanguage input: '" + lang + "'");
        
        if (lang == null || lang.isBlank()) {
            System.out.println("normalizeLanguage: null/blank, returning 'it'");
            return "it"; // Default italiano
        }
        
        String lowerLang = lang.toLowerCase().trim();
        System.out.println("normalizeLanguage lowerLang: '" + lowerLang + "'");
        
        // Gestisci varianti comuni
        if (lowerLang.startsWith("it") || lowerLang.contains("italian")) {
            System.out.println("normalizeLanguage: detected Italian, returning 'it'");
            return "it";
        } else if (lowerLang.startsWith("de") || lowerLang.contains("german")) {
            System.out.println("normalizeLanguage: detected German, returning 'de'");
            return "de";
        } else if (lowerLang.startsWith("en") || lowerLang.contains("english")) {
            System.out.println("normalizeLanguage: detected English, returning 'en'");
            return "en";
        }
        
        // Default a italiano se non riconosciuto
        System.out.println("normalizeLanguage: unrecognized, returning 'it'");
        return "it";
    }

    private String generateItalianRequest(String currentStep, List<String> missingFields) {
        if ("step1".equals(currentStep)) {
            for (String field : missingFields) {
                if (field.contains("data e ora dell'incidente")) {
                    return "Per favore, puoi fornire la data e l'ora dell'incidente? Ad esempio: 'è successo ieri alle 14:30' o 'il 15 marzo alle 9:00'.";
                }
                if (field.contains("data dell'incidente (ho registrato l'orario")) {
                    return field; // Usa il messaggio specifico con l'orario registrato
                }
                if (field.contains("ora dell'incidente (ho registrato la data")) {
                    return field; // Usa il messaggio specifico con la data registrata
                }
                if (field.contains("indirizzo dell'incidente")) {
                    return "Per favore, puoi fornire l'indirizzo completo dell'incidente? Ad esempio: 'Via Roma 123, Milano'.";
                }
                if (field.contains("indirizzo completo (via, numero civico e città)")) {
                    return "Per favore, puoi fornire l'indirizzo completo con via, numero civico e città? Ad esempio: 'Via Roma 123, Milano'.";
                }
            }
        } else if ("step2".equals(currentStep)) {
            for (String field : missingFields) {
                if (field.contains("descrizione di cosa è successo")) {
                    return "Per favore, puoi descrivere cosa è successo? Ad esempio: 'è stato un incidente stradale' o 'c'è stato un incendio'.";
                }
                if (field.contains("dettagli del danno")) {
                    return "Per favore, puoi fornire più dettagli sui danni? Ad esempio: 'la parete è bruciata' o 'l'auto è danneggiata'.";
                }
            }
        }
        
        return "Per favore, fornisci le informazioni mancanti: " + String.join(", ", missingFields) + ".";
    }

    private String generateGermanRequest(String currentStep, List<String> missingFields) {
        if ("step1".equals(currentStep)) {
            if (missingFields.contains("data e ora dell'incidente")) {
                return "Bitte geben Sie Datum und Uhrzeit des Vorfalls an. Zum Beispiel: 'gestern um 14:30' oder 'am 15. März um 9:00'.";
            }
            if (missingFields.contains("indirizzo dell'incidente")) {
                return "Bitte geben Sie die vollständige Adresse des Vorfalls an. Zum Beispiel: 'Hauptstraße 123, Wien'.";
            }
        } else if ("step2".equals(currentStep)) {
            if (missingFields.contains("descrizione di cosa è successo")) {
                return "Bitte beschreiben Sie, was passiert ist. Zum Beispiel: 'es war ein Verkehrsunfall' oder 'es gab einen Brand'.";
            }
        }
        
        return "Bitte geben Sie die fehlenden Informationen an: " + String.join(", ", missingFields) + ".";
    }

    private String generateEnglishRequest(String currentStep, List<String> missingFields) {
        if ("step1".equals(currentStep)) {
            if (missingFields.contains("data e ora dell'incidente")) {
                return "Please provide the date and time of the incident. For example: 'yesterday at 2:30 PM' or 'March 15th at 9:00 AM'.";
            }
            if (missingFields.contains("indirizzo dell'incidente")) {
                return "Please provide the complete address of the incident. For example: 'Main Street 123, London'.";
            }
        } else if ("step2".equals(currentStep)) {
            if (missingFields.contains("descrizione di cosa è successo")) {
                return "Please describe what happened. For example: 'it was a car accident' or 'there was a fire'.";
            }
        }
        
        return "Please provide the missing information: " + String.join(", ", missingFields) + ".";
    }

    @SuppressWarnings("unused")
    private String buildSystemPrompt(String currentStep, List<String> missingFields, String lang) {
        String basePrompt = """
            Sei un assistente esperto per la raccolta di informazioni sui sinistri assicurativi.
            Genera una richiesta specifica e amichevole per ottenere le informazioni mancanti.
            
            Step corrente: {{currentStep}}
            Informazioni mancanti: {{missingFields}}
            
            Regole:
            1. Sii specifico su cosa serve
            2. Usa un tono professionale ma amichevole
            3. Fornisci esempi se utile
            4. Non essere troppo lungo
            5. Chiedi una cosa alla volta se ci sono molte informazioni mancanti
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.missingInfo.requestPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    basePrompt = languageHelper.applyVariables(p.prompt, Map.of(
                            "currentStep", currentStep,
                            "missingFields", String.join(", ", missingFields)
                    ));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }

        return basePrompt.replace("{{currentStep}}", currentStep)
                        .replace("{{missingFields}}", String.join(", ", missingFields));
    }

    @SuppressWarnings("unused")
    private String generateFallbackRequest(List<String> missingFields) {
        if (missingFields.isEmpty()) {
            return "Tutte le informazioni necessarie sono state fornite.";
        }

        StringBuilder message = new StringBuilder("Per procedere ho bisogno delle seguenti informazioni: ");
        
        for (int i = 0; i < missingFields.size(); i++) {
            if (i > 0) {
                message.append(", ");
            }
            message.append(missingFields.get(i));
        }
        message.append(".");

        return message.toString();
    }

    @Tool("Generate step-specific welcome message")
    public String generateStepWelcomeMessageV2(String sessionId, String currentStep, String acceptLanguage) {
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : acceptLanguage;

        try {
            String sys = buildStepWelcomePrompt(currentStep, lang);
            
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from(sys),
                            UserMessage.from("Genera un messaggio di benvenuto per lo step corrente.")
                    ))
                    .build();

            ChatResponse chatResponse = chatModel.chat(chatRequest);
            return chatResponse.aiMessage().text();

        } catch (Exception e) {
            return generateFallbackStepWelcome(currentStep);
        }
    }

    private String buildStepWelcomePrompt(String currentStep, String lang) {
        String basePrompt = """
            Sei un assistente per la raccolta di informazioni sui sinistri assicurativi.
            Genera un messaggio di benvenuto per lo step corrente.
            
            Step: {{currentStep}}
            
            Regole:
            1. Spiega cosa serve per questo step
            2. Sii chiaro e conciso
            3. Usa un tono professionale ma amichevole
            4. Fornisci esempi se utile
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p = languageHelper.getPromptWithLanguage(lang, "fnol.step.welcomePrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    basePrompt = languageHelper.applyVariables(p.prompt, Map.of("currentStep", currentStep));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }

        return basePrompt.replace("{{currentStep}}", currentStep);
    }

    private String generateFallbackStepWelcome(String currentStep) {
        switch (currentStep) {
            case "step1":
                return "Per iniziare la denuncia del sinistro, ho bisogno di sapere dove e quando è successo l'incidente. " +
                       "Puoi fornirmi l'indirizzo completo e la data/ora dell'incidente?";
            case "step2":
                return "Perfetto! Ora ho bisogno di sapere cosa è successo. " +
                       "Puoi descrivermi l'incidente in dettaglio? Se hai foto o video, puoi allegarli.";
            default:
                return "Benvenuto! Come posso aiutarti con la denuncia del sinistro?";
        }
    }

    @Tool("Generate completion message with optional warning")
    public String generateCompletionMessageV2(String sessionId, String acceptLanguage, String warning) {
        System.out.println("=== DEBUG generateCompletionMessageV2 START ===");
        System.out.println("SessionId: " + sessionId);
        System.out.println("AcceptLanguage: " + acceptLanguage);
        System.out.println("Warning: " + warning);
        
        try {
            // Usa direttamente acceptLanguage, con fallback a sessionLanguageContext
            String lang = acceptLanguage;
            if (lang == null || lang.isBlank()) {
                lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "it";
            }
            
            return generateCompletionMessageWithLLM(sessionId, lang, warning);
            
        } catch (Exception e) {
            System.err.println("Error generating completion message: " + e.getMessage());
            e.printStackTrace();
            return "Grazie! Il processo è completato.";
        }
    }

    private String generateCompletionMessageWithLLM(String sessionId, String lang, String warning) {
        try {
            String systemPrompt = createCompletionSystemPrompt(lang, warning);
            
            System.out.println("=== LANGUAGE DEBUG ===");
            System.out.println("Received lang: '" + lang + "'");
            String normalizedLang = normalizeLanguage(lang);
            System.out.println("normalizeLanguage input: '" + lang + "'");
            System.out.println("normalizeLanguage lowerLang: '" + lang.toLowerCase() + "'");
            System.out.println("normalizeLanguage: detected " + (normalizedLang.equals("it") ? "Italian" : "English") + ", returning '" + normalizedLang + "'");
            System.out.println("Normalized lang: '" + normalizedLang + "'");
            System.out.println("=====================");
            
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("Genera un messaggio di completamento per la denuncia del sinistro.")
            )).aiMessage().text();
            
            System.out.println("DEBUG: Generated completion message: " + response);
            return response;
            
        } catch (Exception e) {
            System.err.println("Error in generateCompletionMessageWithLLM: " + e.getMessage());
            e.printStackTrace();
            return "Grazie! Il processo è completato.";
        }
    }

    private String createCompletionSystemPrompt(String lang, String warning) {
        StringBuilder prompt = new StringBuilder();
        
        if ("it".equals(lang)) {
            prompt.append("Sei un assistente di una compagnia assicurativa in una chat. ");
            prompt.append("Devi generare un messaggio di completamento per la denuncia di un sinistro. ");
            prompt.append("Sii caldo, formale il giusto e sintetico. ");
            prompt.append("NON usare saluti iniziali come 'Gentile cliente' o 'Gentile signore'. ");
            prompt.append("NON usare firme email, saluti formali o chiusure come 'Cordiali saluti'. ");
            prompt.append("NON usare placeholder come '[Il Tuo Nome]' o '[La Tua Posizione]'. ");
            prompt.append("NON usare il 'lei' - usa sempre il 'tu' per un tono più amichevole. ");
            prompt.append("Il messaggio deve essere un messaggio di chat diretto e professionale.\n\n");
            
            prompt.append("Struttura del messaggio:\n");
            prompt.append("1. Ringraziare brevemente per le informazioni fornite\n");
            prompt.append("2. Confermare che il processo è completato e i dati salvati\n");
            prompt.append("3. Menzionare la conferma via email\n");
            prompt.append("4. Chiedere se ci sono documenti aggiuntivi (fatture, perizie, contratti, ecc.) - NON foto/video che abbiamo già fatto\n");
            prompt.append("5. Concludere che la denuncia è pronta\n\n");
            
            prompt.append("IMPORTANTE:\n");
            prompt.append("- NON chiedere foto o video (li abbiamo già raccolti)\n");
            prompt.append("- Chiedi solo documenti come fatture, perizie, contratti, certificati\n");
            prompt.append("- Usa un tono caldo ma professionale\n");
            prompt.append("- Sii sintetico, non prolisso\n");
            prompt.append("- Formatta la richiesta di documenti per dare enfasi\n");
            prompt.append("- Usa sempre il 'tu', mai il 'lei'\n\n");
            
            if (warning != null && !warning.isBlank()) {
                prompt.append("IMPORTANTE: Non includere il warning nel messaggio. Il warning sarà gestito separatamente dal frontend.\n");
            }
            
            prompt.append("Genera un messaggio di chat diretto e professionale in italiano usando il 'tu'.");
            
        } else {
            prompt.append("You are an insurance company assistant in a chat. ");
            prompt.append("You need to generate a completion message for a claim report. ");
            prompt.append("Be warm, appropriately formal and concise. ");
            prompt.append("DO NOT use formal greetings like 'Dear client' or 'Dear sir'. ");
            prompt.append("DO NOT use email signatures, formal closings or farewells like 'Best regards'. ");
            prompt.append("DO NOT use placeholders like '[Your Name]' or '[Your Position]'. ");
            prompt.append("Use a friendly, informal tone - avoid overly formal language. ");
            prompt.append("The message must be a direct and professional chat message.\n\n");
            
            prompt.append("Message structure:\n");
            prompt.append("1. Briefly thank for the provided information\n");
            prompt.append("2. Confirm that the process is completed and data saved\n");
            prompt.append("3. Mention email confirmation\n");
            prompt.append("4. Ask if there are additional documents (invoices, reports, contracts, etc.) - NOT photos/videos we already collected\n");
            prompt.append("5. Conclude that the claim is ready\n\n");
            
            prompt.append("IMPORTANT:\n");
            prompt.append("- DO NOT ask for photos or videos (we already collected them)\n");
            prompt.append("- Ask only for documents like invoices, reports, contracts, certificates\n");
            prompt.append("- Use a warm but professional tone\n");
            prompt.append("- Be concise, not verbose\n");
            prompt.append("- Format the document request to give emphasis\n");
            prompt.append("- Use friendly, informal language\n\n");
            
            if (warning != null && !warning.isBlank()) {
                prompt.append("IMPORTANT: Do not include the warning in the message. The warning will be handled separately by the frontend.\n");
            }
            
            prompt.append("Generate a direct and professional chat message in English with a friendly tone.");
        }
        
        return prompt.toString();
    }

    @Tool("Generate warning message for coverage issues")
    public String generateWarningMessageV2(String sessionId, String acceptLanguage, String warningReason) {
        System.out.println("=== DEBUG generateWarningMessageV2 START ===");
        System.out.println("SessionId: " + sessionId);
        System.out.println("AcceptLanguage: " + acceptLanguage);
        System.out.println("WarningReason: " + warningReason);
        
        try {
            // Usa direttamente acceptLanguage, con fallback a sessionLanguageContext
            String lang = acceptLanguage;
            if (lang == null || lang.isBlank()) {
                lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "it";
            }
            
            return generateWarningMessageWithLLM(sessionId, lang, warningReason);
            
        } catch (Exception e) {
            System.err.println("Error generating warning message: " + e.getMessage());
            e.printStackTrace();
            return warningReason != null ? warningReason : "Warning";
        }
    }

    private String generateWarningMessageWithLLM(String sessionId, String lang, String warningReason) {
        try {
            String systemPrompt = createWarningSystemPrompt(lang, warningReason);
            
            System.out.println("=== LANGUAGE DEBUG FOR WARNING ===");
            System.out.println("Received lang: '" + lang + "'");
            String normalizedLang = normalizeLanguage(lang);
            System.out.println("normalizeLanguage input: '" + lang + "'");
            System.out.println("normalizeLanguage lowerLang: '" + lang.toLowerCase() + "'");
            System.out.println("normalizeLanguage: detected " + (normalizedLang.equals("it") ? "Italian" : "English") + ", returning '" + normalizedLang + "'");
            System.out.println("Normalized lang: '" + normalizedLang + "'");
            System.out.println("=====================");
            
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from("Genera un messaggio di warning per il frontend.")
            )).aiMessage().text();
            
            System.out.println("DEBUG: Generated warning message: " + response);
            return response;
            
        } catch (Exception e) {
            System.err.println("Error in generateWarningMessageWithLLM: " + e.getMessage());
            e.printStackTrace();
            return warningReason != null ? warningReason : "Warning";
        }
    }

    private String createWarningSystemPrompt(String lang, String warningReason) {
        StringBuilder prompt = new StringBuilder();
        
        if ("it".equals(lang)) {
            prompt.append("Sei un assistente di una compagnia assicurativa. ");
            prompt.append("Devi generare un messaggio di warning tecnico per il frontend riguardo a problemi di copertura assicurativa. ");
            prompt.append("Il messaggio deve essere conciso, tecnico e diretto. ");
            prompt.append("Non deve essere un messaggio per l'utente finale, ma un warning per il frontend.\n\n");
            
            prompt.append("Motivo del warning: ").append(warningReason != null ? warningReason : "Tipo di danno non coperto dalla polizza").append("\n\n");
            
            prompt.append("Genera un messaggio di warning tecnico in italiano, conciso e diretto.");
            
        } else {
            prompt.append("You are an insurance company assistant. ");
            prompt.append("You need to generate a technical warning message for the frontend regarding insurance coverage issues. ");
            prompt.append("The message should be concise, technical and direct. ");
            prompt.append("It should not be a message for the end user, but a warning for the frontend.\n\n");
            
            prompt.append("Warning reason: ").append(warningReason != null ? warningReason : "Damage type not covered by policy").append("\n\n");
            
            prompt.append("Generate a technical warning message in English, concise and direct.");
        }
        
        return prompt.toString();
    }
}
