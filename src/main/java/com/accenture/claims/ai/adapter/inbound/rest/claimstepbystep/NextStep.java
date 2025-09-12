package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.arjuna.ats.jta.exceptions.NotImplementedException;

public interface NextStep {
    ClaimSubmissionStep nextStep(ClaimSubmissionProgress progress);
}
