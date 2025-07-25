package com.accenture.claims.ai.adapter.inbound.rest.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageHelper {

    private static final String FALLBACK_LANG = "en";
    private static final String RESOURCE_PREFIX = "/prompts/fnol-prompts-";
    private static final String RESOURCE_SUFFIX = ".json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Map<String, Object>> PROMPT_CACHE = new ConcurrentHashMap<>();

    public static class PromptResult {
        public final String language;
        public final String prompt;
        public PromptResult(String language, String prompt) {
            this.language = language;
            this.prompt = prompt;
        }
    }

    public static PromptResult getPromptWithLanguage(String acceptLanguageHeader, String keyPath) {
        List<String> candidates = parseAcceptLanguage(acceptLanguageHeader);
        if (candidates.stream().noneMatch(l -> l.equalsIgnoreCase(FALLBACK_LANG))) {
            candidates.add(FALLBACK_LANG);
        }
        for (String lang : candidates) {
            Map<String,Object> map = loadLanguageMap(lang);
            if (map == null) continue;
            Object v = getNestedValue(map, keyPath);
            if (v != null) {
                return new PromptResult(lang, v.toString());
            }
        }
        throw new RuntimeException("Prompt not found for key "+keyPath);
    }

    /**
     * Parsa Accept-Language e torna in una lista di tag
     * e.g.:
     *   "it-IT,it;q=0.8,en-US;q=0.7,en;q=0.5"
     * Ritorna: ["it-IT","it","en-US","en"]
     */
    private static List<String> parseAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return new ArrayList<>(List.of(FALLBACK_LANG));
        }

        // Dividiamo lista secondo formato standard, e se presente parsiamo la quality
        String[] parts = header.split(",");
        List<LangQ> langqs = new ArrayList<>();
        for (String part : parts) {
            String[] seg = part.trim().split(";");
            String tag = seg[0].trim().toLowerCase(Locale.ROOT);
            double q = 1.0;
            if (seg.length > 1) {
                for (int i = 1; i < seg.length; i++) {
                    String s = seg[i].trim();
                    if (s.startsWith("q=")) {
                        try {
                            q = Double.parseDouble(s.substring(2));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (!tag.isEmpty() && !"*".equals(tag)) {
                langqs.add(new LangQ(tag, q));
            }
        }

        // Manteniamo ordinamento richiesto dall'header
        langqs.sort((a, b) -> Double.compare(b.q, a.q));

        // Espande: "en-gb" -> ["en-gb","en"]
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (LangQ lq : langqs) {
            ordered.add(lq.tag);
            int dash = lq.tag.indexOf('-');
            if (dash > 0) {
                ordered.add(lq.tag.substring(0, dash)); // base language
            }
        }

        return new ArrayList<>(ordered);
    }

    private record LangQ(String tag, double q) {}

    /**
     * Recuper il file, torna null se non trova.
     */
    private static Map<String, Object> loadLanguageMap(String lang) {
        return PROMPT_CACHE.computeIfAbsent(lang, l -> {
            String resource = RESOURCE_PREFIX + l + RESOURCE_SUFFIX;
            try (InputStream is = LanguageHelper.class.getResourceAsStream(resource)) {
                if (is == null) {
                    return null;
                }
                return MAPPER.readValue(is, Map.class);
            } catch (Exception e) {
                throw new RuntimeException("Error loading prompt file: " + resource, e);
            }
        });
    }

    /**
     * Recupera i valori annidati nel file
     */
    @SuppressWarnings("unchecked")
    private static Object getNestedValue(Map<String, Object> map, String keyPath) {
        String[] parts = keyPath.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?> m) {
                current = m.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
