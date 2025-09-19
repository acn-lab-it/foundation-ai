package com.accenture.claims.ai.adapter.outbound.persistence.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@MongoEntity(collection = "policy")
public class PolicyEntity extends PanacheMongoEntity {

    private String policyStatus;
    private ProductReferenceEntity productReference;
    private InsuredPropertyEntity insuredProperty;
    private Date beginDate;
    private Date endDate;
    private String policyNumber;
    private String policyId;
    private List<PolicyHolderEntity> policyHolders;
    private String _class;
    
}
