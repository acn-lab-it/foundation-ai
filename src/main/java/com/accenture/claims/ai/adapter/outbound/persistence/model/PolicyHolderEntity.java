package com.accenture.claims.ai.adapter.outbound.persistence.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class PolicyHolderEntity {
    private String _id;
    private String firstName;
    private String lastName;
    private Date dateOfBirth;
    private String gender;
    private AddressEntity address;
    private List<ContactChannelEntity> contactChannels;
    private String customerId;
    private boolean isPolicyHolderPayer;
    private List<String> roles;
    private String _class;
}
