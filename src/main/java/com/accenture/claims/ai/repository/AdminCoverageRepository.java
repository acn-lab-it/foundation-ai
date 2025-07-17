package com.accenture.claims.ai.repository;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.NoArgsConstructor;

@ApplicationScoped
@NoArgsConstructor
public class AdminCoverageRepository {

    public String adminCoverageCheck(String policyNumber, String date) {

        if(policyNumber.equals("001")) {
            return "COVERED";
        }
        return "NOT_COVERED";

    }

}
