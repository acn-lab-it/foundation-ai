package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.outbound.persistence.model.PolicyEntity;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

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
    public boolean checkPolicy(String policyNumber, String incidentDateIso) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        if (incidentDateIso == null) throw new IllegalArgumentException("incidentDateIso cannot be null");

        Date incidentDate;
        try {
            Instant instant = Instant.parse(incidentDateIso);
            System.out.println("===========================================");
            System.out.println("Instant - Parsed date: "+ instant.toString());
            System.out.println("===========================================");
            incidentDate = Date.from(instant);
        } catch (DateTimeParseException e) {
            // Provo OffsetDateTime se Instant fallisce
            try {
                OffsetDateTime odt = OffsetDateTime.parse(incidentDateIso);
                System.out.println("===========================================");
                System.out.println("OffsetDateTime - Parsed date: "+ odt.toString());
                System.out.println("===========================================");
                incidentDate = Date.from(odt.toInstant());
            } catch (DateTimeParseException ex) {
                // Provo LocalDateTime senza offset
                try {
                    LocalDateTime ldt = LocalDateTime.parse(incidentDateIso.substring(0, 19));
                    System.out.println("===========================================");
                    System.out.println("LocalDateTime - Parsed date: "+ ldt.toString());
                    System.out.println("===========================================");
                    incidentDate = Date.from(Instant.from(ldt));
                } catch (Exception ignored) {
                    // Niente, non è andata
                    System.out.println("===========================================");
                    System.out.println("FALLITO MISERAMENTE");
                    System.out.println("===========================================");
                    throw new IllegalArgumentException("Unable to parse normalized ISO date/time: " + incidentDateIso);
                }
            }
        }
        Date finalIncidentDate = incidentDate;
        return policyRepository.findByPolicyNumber(policyNumber)
                .map(p -> p.getBeginDate().before(finalIncidentDate) && p.getEndDate().after(finalIncidentDate))
                .orElse(false);
    }

    @Tool("Check policy active")
    public boolean checkPolicyActive(String policyNumber) {
        if (policyNumber == null) throw new IllegalArgumentException("Policy number cannot be null");
        return policyRepository.findByPolicyNumber(policyNumber)
                .map(p -> p.getPolicyStatus().equalsIgnoreCase("ACTIVE"))
                .orElse(false);
    }
}
