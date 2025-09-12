package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import java.util.List;

public class StepWhatValidatorAdapter implements StepCompletenessValidator {
    public boolean isStepSpecificComplete(ClaimSubmissionProgress progress) {
        //TODO implement
        return true;
    }

    public ClaimSubmissionStep getStep() {
        return ClaimSubmissionStep.WHAT;
    }

    public List<String> getStepSpecificIncompleteFields(ClaimSubmissionProgress progress) {
        //TODO implement
        return List.of();
    }
}
