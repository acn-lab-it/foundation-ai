package com.accenture.claims.ai.domain.model;

import lombok.Data;

@Data
public class InsuredProperty {
    private String _id;
    private String type;
    private Address address;

}
