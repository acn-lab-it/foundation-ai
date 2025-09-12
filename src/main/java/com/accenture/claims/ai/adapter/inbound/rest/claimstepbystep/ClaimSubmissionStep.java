package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Each enum constant owns a specific subset of EmailParsingResult fields.
 * The owned field identifiers are exposed via getOwnedFields(), so ownership information
 * is co-located with the step definition itself.
 */
public enum ClaimSubmissionStep {
    /**
     * WHEN step owns: incidentDate (and potentially incidentLocation in the future if needed).
     */
    WHEN(new StepWhenValidatorAdapter(), list("incidentDate")),

    /**
     * WHAT step owns: circumstances, whatHappenedCode, whatHappenedContext.
     */
    WHAT(new StepWhatValidatorAdapter(), list("circumstances", "whatHappenedCode", "whatHappenedContext")),

    /**
     * DOCUMENT_UPLOAD step owns: uploadedMedia.
     */
    DOCUMENT_UPLOAD(new StepDocumentUploadValidatorAdapter(), list("uploadedMedia"));

    private final StepCompletenessValidator validator;
    private final List<String> ownedFields;

    ClaimSubmissionStep(StepCompletenessValidator validator, List<String> ownedFields) {
        this.validator = validator;
        this.ownedFields = ownedFields;
    }

    public boolean isComplete(ClaimSubmissionProgress progress) {
        return validator.isComplete(progress);
    }

    /**
     * Returns <an unmodifiable list of EmailParsingResult field identifiers owned by this step.
     */
    public List<String> getOwnedFields() {
        return ownedFields;
    }

    /**
     * Convenience method to check if a field is owned by this step.
     */
    public boolean ownsField(String field) {
        return field != null && ownedFields.contains(field);
    }

    private static List<String> list(String... fields) {
        return Collections.unmodifiableList(Arrays.asList(fields));
    }

    public StepCompletenessValidator getValidator() {
        return validator;
    }
}
