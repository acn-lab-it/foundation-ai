package com.accenture.claims.ai.repository;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class TechCoverageRepository {

    public String techCoverageCheck(String policyNumber, String whatHappenedCode) {
        if(policyNumber.equals("001") && whatHappenedCode.equals("FIR")) {
            return "COVERED";
        }
        return "NOT_COVERED";
    }
}
