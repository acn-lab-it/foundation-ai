package com.accenture.claims.ai.adapter.inbound.rest.chatStorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class FinalOutputStore {

    private final ConcurrentMap<String, ObjectNode> map = new ConcurrentHashMap<>();
    private static final ObjectMapper M = new ObjectMapper();

    /** restituisce (creandolo se serve) l’ObjectNode associato alla sessione */
    public ObjectNode get(String sessionId) {
        return map.computeIfAbsent(sessionId, id -> M.createObjectNode());
    }

    /** patch (shallow merge) dei campi ricevuti */
    public void merge(String sessionId, ObjectNode patch) {
        ObjectNode target = get(sessionId);
        patch.fields().forEachRemaining(e -> target.set(e.getKey(), e.getValue()));
    }

    /** alla fine dello step 6 puoi serializzare e rimuovere */
    public ObjectNode remove(String sessionId) {
        return map.remove(sessionId);
    }
}
