package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.domain.repository.PolicyRepository;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Date;

@ApplicationScoped
public class AdministrativeCheckTool {

    private final PolicyRepository policyRepository;

    public AdministrativeCheckTool(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Tool("Check administrative regularity")
    public boolean checkPolicy(String policyNumber, Date incidentDate) {
        if (policyNumber == null) {
            throw new IllegalArgumentException("Policy number cannot be null");
        }

        return policyRepository.findByPolicyNumber(policyNumber)
                .map(policy ->
                        policy.getBeginDate().before(incidentDate) && policy.getEndDate().after(incidentDate)
                )
                .orElse(false);
    }

    @Tool("Check policy active")
    public boolean checkPolicyActive(String policyNumber) {
        if (policyNumber == null) {
            throw new IllegalArgumentException("Policy number cannot be null");
        }

        return policyRepository.findByPolicyNumber(policyNumber)
                .map(policy ->
                        policy.getPolicyStatus().equalsIgnoreCase("ACTIVE")
                )
                .orElse(false);

    }
}
