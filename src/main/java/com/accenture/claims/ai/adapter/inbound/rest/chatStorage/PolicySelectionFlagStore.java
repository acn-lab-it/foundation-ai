package com.accenture.claims.ai.adapter.inbound.rest.chatStorage;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PolicySelectionFlagStore {
    private final Map<String, Boolean> map = new ConcurrentHashMap<>();
    public boolean isPending(String session)         { return map.getOrDefault(session, false); }
    public void     setPending(String session, boolean v) { map.put(session, v); }
}