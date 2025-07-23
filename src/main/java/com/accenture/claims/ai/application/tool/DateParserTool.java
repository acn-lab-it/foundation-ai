package com.accenture.claims.ai.application.tool;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

@ApplicationScoped
public class DateParserTool {

    public static class IncidentDate {
        public int year;
        public int month;
        public int day;
    }

    // Try ISO-8601 first
    private static Date tryIso(String input) {
        try {
            Instant instant = Instant.parse(input);
            return Date.from(instant);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Some common custom date formats
    private static final DateTimeFormatter[] CUSTOM_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d MM yyyy"),
            DateTimeFormatter.ofPattern("MM d, yyyy"),
            DateTimeFormatter.ofPattern("d MM, yyyy")
    };

    private static Date tryCustomPatterns(String input) {
        for (DateTimeFormatter fmt : CUSTOM_FORMATTERS) {
            try {
                LocalDate ld = LocalDate.parse(input, fmt);
                return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    // NLP fallback using Natty
    private static Date tryNatty(String input) {
        Parser parser = new Parser();
        List<DateGroup> groups = parser.parse(input);
        if (!groups.isEmpty() && !groups.get(0).getDates().isEmpty()) {
            return groups.get(0).getDates().get(0);
        }
        return null;
    }

    @Tool("tryDefaultParse tool used to parse the incident date provided by the user to a java.util.Date")
    public Date tryDefaultParse(IncidentDate incidentDate) {
        return Date.from(Instant.from(LocalDate.of(incidentDate.year, incidentDate.month, incidentDate.day)));
    }

    /**
     * Parses a user‐provided date string into java.util.Date.
     * @param input any of: ISO‐8601, dd-MM-yyyy, “July 14, 2025”, “yesterday”, etc.
     * @return the corresponding Date
     * @throws IllegalArgumentException if no parse strategy succeeds
     */
    @Tool("Convert date to java.util.Date if not possible through the tryDefaultParse tool")
    public Date parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Date string is empty");
        }
        Date d;
        if ((d = tryIso(input)) != null) return d;
        if ((d = tryCustomPatterns(input)) != null) return d;
        if ((d = tryNatty(input)) != null) return d;
        throw new IllegalArgumentException("Unparseable date: '" + input + "'");
    }
}

