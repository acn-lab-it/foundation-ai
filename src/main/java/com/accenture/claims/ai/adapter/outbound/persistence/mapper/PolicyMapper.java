package com.accenture.claims.ai.adapter.outbound.persistence.mapper;

import com.accenture.claims.ai.adapter.outbound.persistence.model.PolicyEntity;
import com.accenture.claims.ai.domain.model.Policy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "cdi", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PolicyMapper {

    PolicyEntity toPolicyEntity(Policy policy);

    Policy toPolicy(PolicyEntity policyEntity);
}
