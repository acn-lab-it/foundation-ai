package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;

public class StepDocumentUploadValidatorAdapter implements StepCompletenessValidator {
    @Override
    public boolean isComplete(ClaimSubmissionProgress progress) {
        if (progress == null) return false;
        EmailParsingResult epr = progress.getEmailParsingResult();
        if (epr == null) return false;
        // DOCUMENT_UPLOAD step is responsible for media/doc attachments
        return epr.getUploadedMedia() != null && !epr.getUploadedMedia().isEmpty();
    }

    @Override
    public ClaimSubmissionStep getStep() {
        return ClaimSubmissionStep.DOCUMENT_UPLOAD;
    }
}
