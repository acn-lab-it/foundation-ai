package com.accenture.claims.ai.adapter.inbound.rest.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

public class LanguageHelper {

    private static final Set<String> SUPPORTED_LANGS = Set.of("it", "en");
    private static final Map<String, Map<String, Object>> PROMPT_CACHE = new HashMap<>();

    public static String resolveBestLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) return "en";
        for (String part : acceptLanguage.split(",")) {
            String lang = part.trim().split(";")[0].toLowerCase(Locale.ROOT);
            if (lang.startsWith("it")) return "it";
            if (lang.startsWith("en")) return "en";
        }
        return "en";
    }

    private static Map<String, Object> loadJson(String lang) {
        String path = "/prompts/fnol-prompts-" + lang + ".json";
        try (InputStream is = LanguageHelper.class.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Missing resource: " + path);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(is, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Error loading prompt file for language: " + lang, e);
        }
    }

    public static String getPrompt(String acceptLanguage, String keyPath) {
        String lang = resolveBestLanguage(acceptLanguage);
        String fallbackLang = "en";

        Map<String, Object> langMap = PROMPT_CACHE.computeIfAbsent(lang, LanguageHelper::loadJson);
        Object prompt = getNestedValue(langMap, keyPath);
        if (prompt == null && !lang.equals(fallbackLang)) {
            Map<String, Object> fallbackMap = PROMPT_CACHE.computeIfAbsent(fallbackLang, LanguageHelper::loadJson);
            prompt = getNestedValue(fallbackMap, keyPath);
        }
        if (prompt == null) {
            throw new RuntimeException("Prompt not found for key: " + keyPath);
        }
        return prompt.toString();
    }

    private static Object getNestedValue(Map<String, Object> map, String keyPath) {
        String[] parts = keyPath.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?, ?>) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}

