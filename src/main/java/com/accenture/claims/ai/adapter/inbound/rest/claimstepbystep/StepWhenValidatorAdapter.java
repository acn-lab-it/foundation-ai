package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import java.util.List;

public class StepWhenValidatorAdapter implements StepCompletenessValidator {
    public boolean isStepSpecificComplete(ClaimSubmissionProgress progress) {
        //TODO implement
        return true;
    }

    public ClaimSubmissionStep getStep() {
        return ClaimSubmissionStep.WHEN;
    }

    public List<String> getStepSpecificIncompleteFields(ClaimSubmissionProgress progress) {
        //TODO implement
        return List.of();
    }
}
