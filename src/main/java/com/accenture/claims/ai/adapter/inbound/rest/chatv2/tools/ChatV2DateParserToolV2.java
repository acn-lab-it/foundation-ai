package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ChatV2DateParserToolV2 {

    @Inject
    ChatModel chatModel;
    @Inject 
    SessionLanguageContext sessionLanguageContext;
    @Inject 
    LanguageHelper languageHelper;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private static final ObjectMapper M = new ObjectMapper();
    private static final Pattern ISO_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})Z?");

    @Tool("Extract only time from natural language (e.g., 'alle 14' -> '14:00:00'). Parameters: sessionId, raw.")
    public String extractTimeOnlyV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String lang = getLanguage(sessionId);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));

        // Prompt specifico per estrarre solo l'ora
        String systemPrompt = String.format("""
            You are an expert at extracting ONLY time information from text.
            Extract the time from the user message and return ONLY the time in HH:mm:ss format.
            
            IMPORTANT: If only time is provided without any date indication, extract ONLY the time.
            Do NOT infer or assume any date. Do NOT use current date or any other date.
            Only extract the time component.
            
            Examples:
            - "alle 14" -> "14:00:00"
            - "alle 14 di pomeriggio" -> "14:00:00"
            - "alle 2 del pomeriggio" -> "14:00:00"
            - "alle 9 di mattina" -> "09:00:00"
            - "alle 20:30" -> "20:30:00"
            - "alle 8" -> "08:00:00"
            - "è successo alle 16" -> "16:00:00" (NO date inference)
            
            Current time context: %s
            
            Return ONLY the time in HH:mm:ss format, or null if no time is found.
            """, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try {
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(raw)
            )).aiMessage().text();
            if (response != null && !response.trim().equals("null") && response.matches("\\d{2}:\\d{2}:\\d{2}")) {
                return response.trim();
            }
        } catch (Exception e) {
            // Fallback to regex patterns
            return extractTimeWithRegex(raw);
        }
        
        return null;
    }

    @Tool("Extract only date from natural language (e.g., 'ieri' -> '2024-01-15'). Parameters: sessionId, raw.")
    public String extractDateOnlyV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String lang = getLanguage(sessionId);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));

        // Prompt specifico per estrarre solo la data con logica di inferenza anno
        String currentDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String currentYear = String.valueOf(now.getYear());
        String currentMonth = String.valueOf(now.getMonthValue());
        String currentDay = String.valueOf(now.getDayOfMonth());
        
        String systemPrompt = String.format("""
            You are an expert at extracting ONLY date information from text.
            Extract the date from the user message and return ONLY the date in YYYY-MM-DD format.
            
            IMPORTANT: Do NOT infer dates from time-only expressions.
            If only time is mentioned (e.g., "alle 16", "è successo alle 14"), return null.
            Only extract actual date information.
            
            IMPORTANT YEAR INFERENCE RULES:
            - If a date is given without year (e.g., "20 dicembre", "15 marzo"):
              * If the date would be in the future compared to today (%s), use the PREVIOUS year
              * If the date would be in the past compared to today (%s), use the CURRENT year (%s)
            
            Examples with current date %s:
            - "ieri" -> "%s"
            - "oggi" -> "%s"
            - "il 15 marzo 2024" -> "2024-03-15" (explicit year)
            - "10 novembre 2025" -> "2025-11-10" (explicit year)
            - "20 dicembre" -> if 20/12 would be future from %s, use %s-12-20, else use %s-12-20
            - "15 marzo" -> if 15/03 would be future from %s, use %s-03-15, else use %s-03-15
            
            Current date context: %s (year: %s, month: %s, day: %s)
            
            Return ONLY the date in YYYY-MM-DD format, or null if no date is found.
            """, 
            currentDate,
            currentDate,
            currentYear,
            currentDate,
            now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            currentDate,
            currentDate,
            String.valueOf(now.getYear() - 1),
            currentYear,
            currentDate,
            String.valueOf(now.getYear() - 1),
            currentYear,
            currentDate,
            currentYear,
            currentMonth,
            currentDay);

        try {
            String response = chatModel.chat(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(raw)
            )).aiMessage().text();
            if (response != null && !response.trim().equals("null") && response.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return response.trim();
            }
        } catch (Exception e) {
            // Fallback to regex patterns
            return extractDateWithRegex(raw, now);
        }
        
            return null;
        }

    @Tool("Normalize any natural-language date/time expression into ISO-8601 string (YYYY-MM-DDThh:mm:ssZ). Parameters: sessionId, raw.")
    public String normalizeV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }

        String lang = getLanguage(sessionId); // fallback interno a 'en'
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));  // puoi forzare ZoneId.of("Europe/Rome") se necessario

        // Carica template multilingua dal DB
        LanguageHelper.PromptResult promptResult =
                languageHelper.getPromptWithLanguage(lang, "dateParser.mainPrompt");

        String prompt = languageHelper.applyVariables(
                promptResult.prompt,
                java.util.Map.of(
                        "now", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        "raw", raw
                )
        );

        // Aggiungi istruzioni per inferenza anno nel prompt
        String enhancedPrompt = addYearInferenceInstructions(prompt, now);
        
        String answer = chatModel.chat(List.of(
                SystemMessage.from("You convert arbitrary date/time strings to ISO-8601."),
                UserMessage.from(enhancedPrompt)
        )).aiMessage().text().trim();

        Matcher m = ISO_PATTERN.matcher(answer);
        if (!m.find()) {
            throw new IllegalArgumentException("LLM did not return ISO-8601: '" + answer + "'");
        }

        String iso = m.group(1) + "Z";

        ObjectNode patch = M.createObjectNode().put("incidentDate", iso);
        finalOutputJSONStore.put("final_output", sessionId, null, patch);

        System.out.println("=== DATE NORMALIZED & SAVED ===");
        System.out.println(iso);
        System.out.println("================================");
        return m.group(1) + "Z";
    }

    @Tool("Tell whether if the natural language contains enough information to calculate the date. Parameters: sessionId, raw.")
    public boolean canCalculateDateV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }
        String lang = getLanguage(sessionId); // fallback interno a 'en'
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));  // puoi forzare ZoneId.of("Europe/Rome") se necessario

        String sys = "You tell whether if the natural language contains enough information to calculate the date. Parameters: now: {{now}}, natural language: {{raw}}. Only accepted answers are 0 or 1.";

        try {
            // Carica template multilingua dal DB
            LanguageHelper.PromptResult promptResult =
                    languageHelper.getPromptWithLanguage(lang, "dateParser.completeDatePrompt");
            sys = promptResult.prompt;
        } catch (Exception e) {
            // usa fallback
        }
        String prompt = languageHelper.applyVariables(
                sys,
                java.util.Map.of(
                        "now", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        "raw", raw
                )
        );

        String answer = chatModel.chat(List.of(
                SystemMessage.from("You tell whether if the natural language contains enough information to calculate the date."),
                UserMessage.from(prompt)
        )).aiMessage().text().trim();

        if(answer.equals("1")) {
            return true;
        }
        if(answer.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("LLM did not return 0 or 1: '" + answer + "'");
    }

    @Tool("Tell whether if the natural language contains enough information to calculate the time. Parameters: sessionId, raw.")
    public boolean canCalculateTimeV2(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }
        String lang = getLanguage(sessionId); // fallback interno a 'en'
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));  // puoi forzare ZoneId.of("Europe/Rome") se necessario

        String sys = "You tell whether if the natural language contains enough information to calculate the time. Parameters: now: {{now}}, natural language: {{raw}}. Only accepted answers are 0 or 1.";

        try {
            // Carica template multilingua dal DB
            LanguageHelper.PromptResult promptResult =
                    languageHelper.getPromptWithLanguage(lang, "dateParser.completeTimePrompt");
            sys = promptResult.prompt;
        } catch (Exception e) {
            // usa fallback
        }
        String prompt = languageHelper.applyVariables(
                sys,
                java.util.Map.of(
                        "now", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        "raw", raw
                )
        );

        String answer = chatModel.chat(List.of(
                SystemMessage.from("You tell whether if the natural language contains enough information to calculate the time."),
                UserMessage.from(prompt)
        )).aiMessage().text().trim();

        if(answer.equals("1")) {
            return true;
        }
        if(answer.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("LLM did not return 0 or 1: '" + answer + "'");
    }

    @Tool("Extract date from user message")
    public String extractDateV2(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        // Simple regex patterns for common date formats
        String[] patterns = {
            "\\b(\\d{1,2})[\\/\\-](\\d{1,2})[\\/\\-](\\d{4})\\b",  // DD/MM/YYYY or DD-MM-YYYY
            "\\b(\\d{4})[\\/\\-](\\d{1,2})[\\/\\-](\\d{1,2})\\b",  // YYYY/MM/DD or YYYY-MM-DD
            "\\b(\\d{1,2})\\s+(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\s+(\\d{4})\\b",  // Italian months
            "\\b(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\s+(\\d{1,2})\\s+(\\d{4})\\b"  // Italian months
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(userMessage);
            if (m.find()) {
                return normalizeV2(null, m.group());
            }
        }

        return null;
    }

    @Tool("Extract location from user message")
    public String extractLocationV2(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        // Simple location extraction - look for common patterns
        String[] locationPatterns = {
            "via\\s+[^,]+",  // "via ..."
            "viale\\s+[^,]+",  // "viale ..."
            "piazza\\s+[^,]+",  // "piazza ..."
            "corso\\s+[^,]+",  // "corso ..."
            "\\d+\\s+[^,]+\\s+(strada|via|viale|piazza|corso)",  // "123 via ..."
        };

        for (String pattern : locationPatterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(userMessage);
            if (m.find()) {
                return m.group().trim();
            }
        }

        return null;
    }

    private String extractTimeWithRegex(String raw) {
        // Pattern per "alle 14", "alle 14:30", "alle 2 del pomeriggio"
        Pattern timePattern = Pattern.compile("alle\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(?:di\\s+(mattina|pomeriggio|sera))?");
        Matcher matcher = timePattern.matcher(raw.toLowerCase());
        
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            String period = matcher.group(3);
            
            // Gestisci periodi del giorno
            if ("pomeriggio".equals(period) && hour < 12) {
                hour += 12;
            } else if ("sera".equals(period) && hour < 12) {
                hour += 12;
            }
            
            return String.format("%02d:%02d:00", hour, minute);
        }
        
        // Pattern per "14:30", "9:00"
        Pattern directTimePattern = Pattern.compile("(\\d{1,2}):(\\d{2})");
        matcher = directTimePattern.matcher(raw);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            return String.format("%02d:%02d:00", hour, minute);
        }

        return null;
    }

    private String extractDateWithRegex(String raw, ZonedDateTime now) {
        // Pattern per "ieri", "oggi", "domani"
        if (raw.toLowerCase().contains("ieri")) {
            return now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (raw.toLowerCase().contains("oggi")) {
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        if (raw.toLowerCase().contains("domani")) {
            return now.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        
        // Pattern per date specifiche "15 marzo 2024"
        Pattern datePattern = Pattern.compile("(\\d{1,2})\\s+(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\s+(\\d{4})");
        Matcher matcher = datePattern.matcher(raw.toLowerCase());
        if (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            String month = matcher.group(2);
            int year = Integer.parseInt(matcher.group(3));
            
            int monthNum = getMonthNumber(month);
            return String.format("%04d-%02d-%02d", year, monthNum, day);
        }
        
        // Pattern per date senza anno "15 marzo", "20 dicembre"
        Pattern dateWithoutYearPattern = Pattern.compile("(\\d{1,2})\\s+(gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)");
        matcher = dateWithoutYearPattern.matcher(raw.toLowerCase());
        if (matcher.find()) {
            int day = Integer.parseInt(matcher.group(1));
            String month = matcher.group(2);
            int monthNum = getMonthNumber(month);
            
            // Logica di inferenza anno
            int year = inferYear(day, monthNum, now);
            return String.format("%04d-%02d-%02d", year, monthNum, day);
        }
        
                return null;
            }
            
    private int getMonthNumber(String month) {
        return switch (month.toLowerCase()) {
            case "gennaio" -> 1;
            case "febbraio" -> 2;
            case "marzo" -> 3;
            case "aprile" -> 4;
            case "maggio" -> 5;
            case "giugno" -> 6;
            case "luglio" -> 7;
            case "agosto" -> 8;
            case "settembre" -> 9;
            case "ottobre" -> 10;
            case "novembre" -> 11;
            case "dicembre" -> 12;
            default -> 1;
        };
    }

    private int inferYear(int day, int month, ZonedDateTime now) {
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        int currentDay = now.getDayOfMonth();
        
        // Crea la data con l'anno corrente
        ZonedDateTime dateWithCurrentYear = ZonedDateTime.of(currentYear, month, day, 0, 0, 0, 0, now.getZone());
        
        // Se la data con l'anno corrente è nel futuro, usa l'anno precedente
        if (dateWithCurrentYear.isAfter(now)) {
            return currentYear - 1;
        } else {
            return currentYear;
        }
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

    private String addYearInferenceInstructions(String originalPrompt, ZonedDateTime now) {
        String currentDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String currentYear = String.valueOf(now.getYear());
        
        return originalPrompt + "\n\n" + String.format("""
            IMPORTANT: For dates without year (e.g., "20 dicembre", "15 marzo"):
            - If the date would be in the future compared to today (%s), use the PREVIOUS year (%s)
            - If the date would be in the past compared to today (%s), use the CURRENT year (%s)
            
            Examples:
            - "20 dicembre" -> if 20/12 would be future from %s, use %s-12-20T00:00:00Z, else use %s-12-20T00:00:00Z
            - "15 marzo" -> if 15/03 would be future from %s, use %s-03-15T00:00:00Z, else use %s-03-15T00:00:00Z
            """, 
            currentDate,
            String.valueOf(now.getYear() - 1),
            currentDate,
            currentYear,
            currentDate,
            String.valueOf(now.getYear() - 1),
            currentYear,
            currentDate,
            String.valueOf(now.getYear() - 1),
            currentYear);
    }
}
