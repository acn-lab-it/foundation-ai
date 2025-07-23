package com.accenture.claims.ai.domain.model;

import lombok.Data;

@Data
public class Address {
    private String fullAddress;
    private StreetDetails streetDetails;
    private String country;
    private String state;
    private String countryCode;
    private String city;
    private String postalCode;
    private String _class;

}
