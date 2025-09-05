package com.accenture.claims.ai.adapter.inbound.rest.helpers;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class LanguageHelper {

    private static final String FALLBACK_LANG = "en";
    private static final String COLLECTION_NAME = "prompts";

    @Inject
    MongoClient mongo;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "local_db")
    String dbName;

    // Cache per lingua -> mappa del documento (senza _id)
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public static class PromptResult {
        public final String language;
        public final String prompt;
        public PromptResult(String language, String prompt) {
            this.language = language;
            this.prompt = prompt;
        }
    }

    private MongoCollection<Document> collection() {
        return mongo.getDatabase(dbName).getCollection(COLLECTION_NAME);
    }

    public PromptResult getPromptWithLanguage(String acceptLanguageHeader, String keyPath) {
        if(acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) acceptLanguageHeader = FALLBACK_LANG;
        List<String> candidates = parseAcceptLanguage(acceptLanguageHeader);
        if (candidates.stream().noneMatch(l -> l.equalsIgnoreCase(FALLBACK_LANG))) {
            candidates.add(FALLBACK_LANG);
        }
        for (String lang : candidates) {
            Map<String, Object> map = loadLanguageMap(lang);
            if (map == null) continue;
            Object v = getNestedValue(map, keyPath);
            if (v != null) {
                return new PromptResult(lang, v.toString());
            }
        }
        throw new IllegalStateException("Prompt not found for key " + keyPath);
    }

    public Optional<String> getPrompt(String language, String keyPath) {
        if (language == null || language.isBlank()) language = FALLBACK_LANG;
        List<String> order = new ArrayList<>();
        order.add(language.toLowerCase(Locale.ROOT));
        if (!language.equalsIgnoreCase(FALLBACK_LANG)) {
            order.add(FALLBACK_LANG);
        }
        for (String lang : order) {
            Map<String, Object> map = loadLanguageMap(lang);
            if (map == null) continue;
            Object v = getNestedValue(map, keyPath);
            if (v != null) return Optional.of(v.toString());
        }
        return Optional.empty();
    }

    public String applyVariables(String text, Map<String,String> vars) {
        if (text == null || vars == null || vars.isEmpty()) return text;
        String r = text;
        for (var e : vars.entrySet()) {
            r = r.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return r;
    }

    public void invalidate(String language) {
        if (language != null) cache.remove(language.toLowerCase(Locale.ROOT));
    }

    public void invalidateAll() {
        cache.clear();
    }

    // ---- Internals ----

    private Map<String, Object> loadLanguageMap(String lang) {
        String key = lang.toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, l -> {
            Document doc = collection().find(Filters.eq("id", l)).first();
            if (doc == null) return null;
            Map<String, Object> map = new HashMap<>(doc);
            map.remove("id");
            return map;
        });
    }

    private List<String> parseAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return new ArrayList<>(List.of(FALLBACK_LANG));
        }
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
                        try { q = Double.parseDouble(s.substring(2)); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (!tag.isEmpty() && !"*".equals(tag)) {
                langqs.add(new LangQ(tag, q));
            }
        }
        langqs.sort((a, b) -> Double.compare(b.q, a.q));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (LangQ lq : langqs) {
            ordered.add(lq.tag);
            int dash = lq.tag.indexOf('-');
            if (dash > 0) {
                ordered.add(lq.tag.substring(0, dash));
            }
        }
        return new ArrayList<>(ordered);
    }

    private record LangQ(String tag, double q) {}

    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String keyPath) {
        String[] parts = keyPath.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map<?,?> m) {
                current = m.get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
