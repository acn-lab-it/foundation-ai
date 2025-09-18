package com.accenture.claims.ai.domain.repository;

import com.accenture.claims.ai.domain.model.InsuredProperty;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.model.PolicyHolder;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository {


    Optional<Policy> findByPolicyNumber(String policyNumber);

    List<Policy> findByCustomerId(String customerId);

    Optional<InsuredProperty> findInsuredPropertyByPolicyNumber(String policyNumber);

    Optional<PolicyHolder> findPolicyHolderByPolicyNumber(String policyNumber);

    void put(Policy policy);

}
