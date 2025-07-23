package com.accenture.claims.ai.adapter.outbound.persistence.model;

import lombok.Data;

@Data
public class ProductReferenceEntity {
    private String version;
    private String name;
    private String groupNameApl;
    private String groupName;
    private String code;
}
