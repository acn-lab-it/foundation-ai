package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.arjuna.ats.jta.exceptions.NotImplementedException;

public class NextStepAdapter implements NextStep{
    public ClaimSubmissionStep nextStep(ClaimSubmissionProgress progress) throws NotImplementedException {
        for(ClaimSubmissionStep step : ClaimSubmissionStep.values()){
            if(!step.isComplete(progress)){
                return step;
            }
        }
        throw new NotImplementedException("No more steps available");
    }
}
