package com.accenture.claims.ai.application.tool;

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
public class DateParserTool {

    @Inject ChatModel chatModel;
    @Inject SessionLanguageContext sessionLanguageContext;
    @Inject LanguageHelper languageHelper;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private static final ObjectMapper M = new ObjectMapper();
    private static final Pattern ISO_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})Z?");

    @Tool("Normalize any natural-language date/time expression into ISO-8601 string (YYYY-MM-DDThh:mm:ssZ). Parameters: sessionId, raw.")
    public String normalize(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }

        String lang = sessionLanguageContext.getLanguage(sessionId); // fallback interno a 'en'
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

        String answer = chatModel.chat(List.of(
                SystemMessage.from("You convert arbitrary date/time strings to ISO-8601."),
                UserMessage.from(prompt)
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
    public boolean canCalculateDate(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }
        String lang = sessionLanguageContext.getLanguage(sessionId); // fallback interno a 'en'
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
    public boolean canCalculateTime(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }
        String lang = sessionLanguageContext.getLanguage(sessionId); // fallback interno a 'en'
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
}
