package com.accenture.claims.ai.adapter.outbound.persistence.model;

import lombok.Data;

@Data
public class InsuredPropertyEntity{
    private String _id;
    private String type;
    private AddressEntity address;
}
