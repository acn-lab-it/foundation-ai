package com.accenture.claims.ai.domain.model;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class Policy {

    private String policyStatus;
    private ProductReference productReference;
    private InsuredProperty insuredProperty;
    private Date beginDate;
    private Date endDate;
    private String policyNumber;
    private List<PolicyHolder> policyHolders;
    private String _class;

}
