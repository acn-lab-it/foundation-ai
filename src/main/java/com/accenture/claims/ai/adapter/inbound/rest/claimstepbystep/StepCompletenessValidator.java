package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public interface StepCompletenessValidator {
    boolean isStepSpecificComplete(ClaimSubmissionProgress progress);
    ClaimSubmissionStep getStep();

    default List<String> getIncompleteFields(ClaimSubmissionProgress progress) {
        List<String> incompleteFields = new ArrayList<>();
        incompleteFields.addAll(getOwnBlankFields(progress));
        incompleteFields.addAll(getStepSpecificIncompleteFields(progress));
        return incompleteFields;
    }

    default List<String> getOwnBlankFields(ClaimSubmissionProgress progress) {
        List<String> incompleteFields = new ArrayList<>();
        for (String f : this.getStep().getOwnedFields()) {
            if (isFieldBlank(f, progress)) {
                incompleteFields.add(f);
            }
        }
        return incompleteFields;
    }

    List<String> getStepSpecificIncompleteFields(ClaimSubmissionProgress progress);

    default boolean isComplete(ClaimSubmissionProgress progress) {
        if (progress == null) return false;
        EmailParsingResult epr = progress.getEmailParsingResult();
        if (epr == null) return false;
        return isOwnFieldsNotBlank(progress) && isStepSpecificComplete(progress);
    }
    default boolean isOwnFieldsNotBlank(ClaimSubmissionProgress progress){
        for (String f : this.getStep().getOwnedFields()) {
            if (isFieldBlank(f, progress)) {
                return false;
            }
        }
        return true;
    }

    default boolean isFieldBlank(String fieldName, ClaimSubmissionProgress progress) {
        String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        try {
            var m = progress.getEmailParsingResult().getClass().getMethod(getter);
            Object attribute = m.invoke(progress.getEmailParsingResult());
            if (attribute == null) {
                return false;
            }
            if (attribute instanceof String s && s.isBlank()) {
                return false;
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

}
