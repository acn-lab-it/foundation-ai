package com.accenture.claims.ai.adapter.inbound.rest;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class GuardrailsContext {
    private String sessionId;
    private String systemPrompt;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}