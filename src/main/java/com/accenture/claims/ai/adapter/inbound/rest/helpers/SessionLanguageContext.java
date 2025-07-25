package com.accenture.claims.ai.adapter.inbound.rest.helpers;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class SessionLanguageContext {
    private final ConcurrentMap<String, String> map = new ConcurrentHashMap<>();

    public void setLanguage(String sessionId, String lang) {
        map.put(sessionId, lang);
    }

    public String getLanguage(String sessionId) {
        return map.getOrDefault(sessionId, "en");
    }
}