package com.accenture.claims.ai.adapter.outbound.persistence.repository;

import com.accenture.claims.ai.adapter.outbound.persistence.mapper.PolicyMapper;
import com.accenture.claims.ai.adapter.outbound.persistence.model.PolicyEntity;
import com.accenture.claims.ai.domain.model.InsuredProperty;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.model.PolicyHolder;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@ApplicationScoped
@Priority(20)
public class PolicyRepositoryAdapter implements PolicyRepository, PanacheMongoRepository<PolicyEntity> {

    private final PolicyMapper policyMapper;

    public PolicyRepositoryAdapter(PolicyMapper policyMapper) {
        this.policyMapper = policyMapper;
    }

    @Tool("Search for a policy by policyNumber")
    @Override
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return find("policyNumber", policyNumber)
                .firstResultOptional().map(policyMapper::toPolicy);
    }

    @Tool("Search for policies by customerId")
    @Override
    public List<Policy> findByCustomerId(String customerId) {
        return list("policyHolders.customerId", customerId)
                .stream().map(policyMapper::toPolicy).collect(Collectors.toList());
    }

    @Override
    public Optional<InsuredProperty> findInsuredPropertyByPolicyNumber(String policyNumber) {
        return find("policyNumber", policyNumber).firstResultOptional().map(policyMapper::toPolicy)
                .map(Policy::getInsuredProperty);
    }

    @Override
    public Optional<PolicyHolder> findPolicyHolderByPolicyNumber(String policyNumber) {
        return find("policyNumber", policyNumber).firstResultOptional().map(policyMapper::toPolicy)
                .map(policy -> policy.getPolicyHolders().getFirst());
    }
}
