package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;

public class StepWhatValidatorAdapter implements StepCompletenessValidator {
    @Override
    public boolean isComplete(ClaimSubmissionProgress progress) {
        if (progress == null) return false;
        EmailParsingResult epr = progress.getEmailParsingResult();
        if (epr == null) return false;
        ClaimSubmissionStep.WHAT.getOwnedFields().forEach(f -> {
            // controlla se gli owned fields sono null o bianchi

        });
        // WHAT step is responsible for circumstances / what happened classification
        return epr.getCircumstances() != null || epr.getWhatHappenedCode() != null || epr.getWhatHappenedContext() != null;
    }

    @Override
    public ClaimSubmissionStep getStep() {
        return ClaimSubmissionStep.WHAT;
    }
}
