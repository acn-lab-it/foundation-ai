package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import java.util.List;

public class StepDocumentUploadValidatorAdapter implements StepCompletenessValidator {
    public boolean isStepSpecificComplete(ClaimSubmissionProgress progress) {
        //TODO implement
        return true;
    }

    public ClaimSubmissionStep getStep() {
        return ClaimSubmissionStep.DOCUMENT_UPLOAD;
    }

    public List<String> getStepSpecificIncompleteFields(ClaimSubmissionProgress progress) {
        //TODO implement
        return List.of();
    }
}
