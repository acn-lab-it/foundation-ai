package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import java.lang.reflect.InvocationTargetException;

public interface StepCompletenessValidator {
    boolean isComplete(ClaimSubmissionProgress progress);
    ClaimSubmissionStep getStep();
    default boolean isOwnFieldsNotBlank(ClaimSubmissionProgress progress){
        for (String f : this.getStep().getOwnedFields()) {
            String getter = "get" + Character.toUpperCase(f.charAt(0)) + f.substring(1);
            try {
                var m = progress.getEmailParsingResult().getClass().getMethod(getter);
                Object attribute = m.invoke(progress.getEmailParsingResult());
                if(attribute == null){
                    return false;
                }
                if(attribute instanceof String s && s.isBlank()){
                    return false;
                }
            } catch (NoSuchMethodException e) {
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }
}
