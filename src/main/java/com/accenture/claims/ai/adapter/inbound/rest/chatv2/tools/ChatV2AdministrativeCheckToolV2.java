package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;

@ApplicationScoped
public class ChatV2AdministrativeCheckToolV2 {

    @Inject
    PolicyRepository policyRepository;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;
    private final ObjectMapper M = new ObjectMapper();

    @Tool("Check policy existence")
    public boolean checkPolicyExistenceV2(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber).isPresent();
    }

    @Tool("Get policy details")
    public Optional<Policy> getPolicyDetailsV2(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber);
    }

    @Tool("Check administrative regularity. incidentDateIso must be ISO-8601 (e.g. 2025-07-14T22:00:00Z)")
    public boolean checkPolicyV2(String sessionId, String policyNumber) {

        System.out.println("=== DEBUG ADMINISTRATIVE CHECK START ===");
        System.out.println("SessionId: " + sessionId);
        System.out.println("PolicyNumber: " + policyNumber);

        ObjectNode fo = finalOutputJSONStore.get("final_output", sessionId);
        String incidentDateIso = fo.path("incidentDate").asText(null);
        System.out.println("IncidentDateIso from finalOutput: " + incidentDateIso);
        
        if (incidentDateIso == null || incidentDateIso.isBlank()) {
            throw new IllegalStateException("incidentDate assente da FINAL_OUTPUT");
        }

        Date incidentDate;
        try {
            incidentDate = Date.from(Instant.parse(incidentDateIso));
            System.out.println("Parsed incidentDate: " + incidentDate);
        } catch (DateTimeParseException ex) {           // fallback a OffsetDateTime (es. ±hh:mm)
            incidentDate = Date.from(OffsetDateTime.parse(incidentDateIso).toInstant());
            System.out.println("Parsed incidentDate (fallback): " + incidentDate);
        }

        Date finalIncidentDate = incidentDate;
        
        Optional<Policy> policyOpt = policyRepository.findByPolicyNumber(policyNumber);
        if (policyOpt.isEmpty()) {
            System.out.println("ERROR: Policy not found for policyNumber: " + policyNumber);
            return false;
        }
        
        Policy policy = policyOpt.get();
        System.out.println("Policy found:");
        System.out.println("  beginDate: " + policy.getBeginDate());
        System.out.println("  endDate: " + policy.getEndDate());
        System.out.println("  incidentDate: " + finalIncidentDate);
        
        boolean beginDateCheck = policy.getBeginDate().before(finalIncidentDate);
        boolean endDateCheck = policy.getEndDate().after(finalIncidentDate);
        boolean passed = beginDateCheck && endDateCheck;
        
        System.out.println("Date comparison results:");
        System.out.println("  beginDate.before(incidentDate): " + beginDateCheck + " (" + policy.getBeginDate() + " < " + finalIncidentDate + ")");
        System.out.println("  endDate.after(incidentDate): " + endDateCheck + " (" + policy.getEndDate() + " > " + finalIncidentDate + ")");
        System.out.println("  FINAL RESULT (passed): " + passed);
        System.out.println("=== DEBUG ADMINISTRATIVE CHECK END ===");

        ObjectNode patch = M.createObjectNode();
        patch.putObject("administrativeCheck").put("passed", passed);

        finalOutputJSONStore.put("final_output", sessionId, null, patch); // merge alla radice

        return passed;
    }

    @Tool("Check policy active")
    public boolean checkPolicyActiveV2(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber)
                .map(p -> p.getPolicyStatus().equalsIgnoreCase("ACTIVE"))
                .orElse(false);
    }
}
