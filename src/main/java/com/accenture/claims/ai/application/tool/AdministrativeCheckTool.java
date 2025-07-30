package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.outbound.persistence.model.PolicyEntity;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;

@ApplicationScoped
public class AdministrativeCheckTool {

    private final PolicyRepository policyRepository;
    private static final ObjectMapper M = new ObjectMapper();
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    public AdministrativeCheckTool(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Tool("Detect policy existence based on user input.")
    public boolean checkPolicyExistence(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber).isPresent();
    }

    @Tool("Retrieve Policy data by provided number. Useful to verify user provided information.")
    public Optional<Policy> getPolicyDetails(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber);
    }

    @Tool("Check administrative regularity. incidentDateIso must be ISO-8601 (e.g. 2025-07-14T22:00:00Z)")
    public boolean checkPolicy(String sessionId, String policyNumber) {

        ObjectNode fo = finalOutputJSONStore.get("final_output", sessionId);
        String incidentDateIso = fo.path("incidentDate").asText(null);
        if (incidentDateIso == null || incidentDateIso.isBlank()) {
            throw new IllegalStateException("incidentDate assente da FINAL_OUTPUT");
        }

        Date incidentDate;
        try {
            incidentDate = Date.from(Instant.parse(incidentDateIso));
        } catch (DateTimeParseException ex) {           // fallback a OffsetDateTime (es. ±hh:mm)
            incidentDate = Date.from(OffsetDateTime.parse(incidentDateIso).toInstant());
        }

        Date finalIncidentDate = incidentDate;
        boolean passed = policyRepository.findByPolicyNumber(policyNumber)
                .map(p -> p.getBeginDate().before(finalIncidentDate) && p.getEndDate().after(finalIncidentDate))
                .orElse(false);

        ObjectNode patch = M.createObjectNode();
        patch.putObject("administrativeCheck").put("passed", passed);

        finalOutputJSONStore.put("final_output", sessionId, null, patch); // merge alla radice

        return passed;
    }


    @Tool("Check policy active")
    public boolean checkPolicyActive(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber)
                .map(p -> p.getPolicyStatus().equalsIgnoreCase("ACTIVE"))
                .orElse(false);
    }
}
