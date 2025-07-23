package com.accenture.claims.ai.adapter.outbound.persistence.model;

import lombok.Data;

@Data
public class StreetDetailsEntity {
    private String name;
    private String nameType;
    private String number;
    private String numberType;
}
