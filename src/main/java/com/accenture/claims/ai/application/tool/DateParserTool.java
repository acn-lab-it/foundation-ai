package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DateParserTool {

    @Inject ChatModel chatModel;
    @Inject SessionLanguageContext sessionLanguageContext;

    private static final Pattern ISO_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})Z?");

    @Tool("Normalize any natural-language date/time expression into ISO-8601 string (YYYY-MM-DDThh:mm:ssZ). Parameters: sessionId, raw.")
    public String normalize(String sessionId, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }

        String lang = sessionLanguageContext.getLanguage(sessionId); // fallback interno a 'en'
        ZonedDateTime now = ZonedDateTime.now(); // puoi forzare ZoneId.of("Europe/Rome") se necessario

        // Carica template multilingua
        LanguageHelper.PromptResult promptResult = LanguageHelper.getPromptWithLanguage(lang, "dateParser.mainPrompt");
        String prompt = promptResult
                .prompt
                .replace("{{now}}", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .replace("{{raw}}", raw);

        var answer = chatModel.chat(List.of(
                SystemMessage.from("You convert arbitrary date/time strings to ISO-8601."),
                UserMessage.from(prompt)
        )).aiMessage().text().trim();

        Matcher m = ISO_PATTERN.matcher(answer);
        if (!m.find()) {
            throw new IllegalArgumentException("LLM did not return ISO-8601: '" + answer + "'");
        }
        return m.group(1) + "Z";
    }
}
