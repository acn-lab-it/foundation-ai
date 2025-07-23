package com.accenture.claims.ai.domain.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class PolicyHolder {
    private String _id;
    private String firstName;
    private String lastName;
    private Date dateOfBirth;
    private String gender;
    private Address address;
    private List<ContactChannel> contactChannels;
    private String customerId;
    private boolean isPolicyHolderPayer;
    private List<String> roles;
    private String _class;
}
