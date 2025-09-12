package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

public class StepWhenValidatorAdapter implements StepCompletenessValidator {
    @Override
    public boolean isComplete(ClaimSubmissionProgress progress) {
        if (progress == null || progress.getEmailParsingResult() == null) {
            return false;
        }
        // Complete when incident date is present
        return progress.getEmailParsingResult().getIncidentDate() != null;
    }

    @Override
    public ClaimSubmissionStep getStep() {
        return ClaimSubmissionStep.WHEN;
    }
}
