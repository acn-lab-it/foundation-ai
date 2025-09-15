package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NextStepAdapter implements NextStep{
    public ClaimSubmissionStep nextStep(ClaimSubmissionProgress progress) {
        for(ClaimSubmissionStep step : ClaimSubmissionStep.values()){
            if(!step.isComplete(progress)){
                return step;
            }
        }
        throw new RuntimeException("No more steps available");
    }
}
