package com.accenture.claims.ai.adapter.outbound.persistence.model;

import lombok.Data;

@Data
public class AddressEntity {
    private String fullAddress;
    private StreetDetailsEntity streetDetails;
    private String country;
    private String state;
    private String countryCode;
    private String city;
    private String postalCode;
    private String _class;
}
