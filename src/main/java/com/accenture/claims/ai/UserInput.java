package com.accenture.claims.ai;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Data;

@ApplicationScoped
@Data
public class UserInput {
    private String policyNumber;
    private String whc;
}
