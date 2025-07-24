package com.accenture.claims.ai.application.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DateParserTool {

    @Inject ChatModel chatModel;

    @Tool("Normalize any natural-language date/time expression into ISO-8601 string (YYYY-MM-DDThh:mm:ssZ).")
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty date/time input");
        }

        ZonedDateTime now = ZonedDateTime.now();
        String prompt = """
                You are a date-time normalization assistant.
                Current reference: %s.
                Convert the user's date/time expression into a single ISO-8601 timestamp with time *and* UTC 'Z' suffix.

                Rules:
                - Italian or English input.
                - Resolve relative expressions (e.g. "ieri", "next monday") from the reference.
                - If time is missing, use 00:00:00.
                - Output ONLY one line: YYYY-MM-DDThh:mm:ssZ
                User input: %s
                """.formatted(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), raw);

        var answer = chatModel.chat(List.of(
                SystemMessage.from("You convert arbitrary date/time strings to ISO-8601."),
                UserMessage.from(prompt)
        )).aiMessage().text().trim();

        // Regex: 2025-07-14 | T | 22:00:00 | Z
        Pattern p = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})Z?");
        Matcher m = p.matcher(answer);
        if (!m.find()) {
            throw new IllegalArgumentException("LLM did not return ISO-8601: '" + answer + "'");
        }
        String core = m.group(1);
        return core + "Z";
    }
}
