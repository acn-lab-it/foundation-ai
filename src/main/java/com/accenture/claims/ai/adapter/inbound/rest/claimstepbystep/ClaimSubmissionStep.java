package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Each enum constant owns a specific subset of ParsingResult fields.
 * The owned field identifiers are exposed via getOwnedFields(), so ownership information
 * is co-located with the step definition itself.
 */
@Getter
public enum ClaimSubmissionStep {
    /**
     * WHEN step owns: incidentDate (and potentially incidentLocation in the future if needed).
     */
    WHEN_AND_WHERE(new StepWhenValidatorAdapter(), list("incidentDate", "incidentTime", "incidentCountry", "incidentCity", "incidentStreet", "incidentStreetNumber"), "quando"),

    /**
     * WHAT step owns: circumstances, whatHappenedCode, whatHappenedContext.
     */
    WHAT(new StepWhatValidatorAdapter(), list("circumstances", "whatHappenedCode", "whatHappenedContext", "uploadedMedia"), "cosaSuccesso"),

    /**
     * DOCUMENT_UPLOAD step owns: uploadedMedia.
     */
    DOCUMENT_UPLOAD(new StepDocumentUploadValidatorAdapter(), list(), "documenti");

    private final StepCompletenessValidator validator;
    private final List<String> ownedFields;
    private final String label;

    ClaimSubmissionStep(StepCompletenessValidator validator, List<String> ownedFields, String stepLabel) {
        this.validator = validator;
        this.ownedFields = ownedFields;
        this.label = stepLabel;
    }

    public boolean isComplete(ClaimSubmissionProgress progress) {
        return validator.isComplete(progress);
    }


    private static List<String> list(String... fields) {
        return Collections.unmodifiableList(Arrays.asList(fields));
    }

}
