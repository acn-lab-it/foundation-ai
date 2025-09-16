package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Path("/api/old/fnol/chat")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class FnolClaimFormResource {

    @Inject
    StepMessageTool stepMessageTool;
    @Inject
    StepParseMessageTool stepParseMessageTool;
    @Inject
    WelcomeTool welcomeTool;
    @Inject
    ClaimSubmissionProgressRepository repository;

    public static class ChatResponseDto {
        public String sessionId;
        public String answer;
        public StepData stepperData;
        public int currentState;

        public ChatResponseDto(String sessionId, String answer, ClaimSubmissionProgressRepository repository) {
            this.sessionId = sessionId;
            this.answer = answer;

            ClaimSubmissionProgress progress = repository.findBySessionId(sessionId);
            this.stepperData = new StepData();
            List<StepResponseElement> list = new ArrayList<>();
            int nextStep = 0;
            boolean stepFound = false;
            for (ClaimSubmissionStep step : ClaimSubmissionStep.values()) {
                boolean isStepComplete = step.isComplete(progress);
                list.add(new StepResponseElement(step.getLabel(), isStepComplete));
                if (!stepFound && !isStepComplete) {
                    this.currentState = nextStep;
                }
                nextStep++;
            }
            stepperData.stepList = list;

        }
    }

    public static class StepData {
        public List<StepResponseElement> stepList;

    }

    @Data
    @AllArgsConstructor
    public static class StepResponseElement {
        public String label;
        public boolean completed;
    }

    @POST
    @Path("/welcome")
    @ActivateRequestContext
    public Response welcome(
            @NotBlank @QueryParam("userEmail") String userEmail,
            @NotBlank @QueryParam("policyNumber") String policyNumber,
            @HeaderParam("Accept-Language") String acceptLanguage
    ) {
        String sessionId = UUID.randomUUID().toString();
        var progress = new ParsingResult();
        progress.setPolicyNumber(policyNumber);
        progress.setEmail(userEmail);
        progress.setSessionId(sessionId);
        repository.upsertBySessionId(sessionId, progress);

        String chatResponse = welcomeTool.welcomeMsg(policyNumber, userEmail, acceptLanguage, sessionId);
        return Response.ok(new ChatResponseDto(sessionId, chatResponse, repository)).build();

    }

    @POST
    @Path("/step")
    @ActivateRequestContext
    public Response chat(
            @QueryParam("sessionId") String sessionId,
            @NotBlank String userChatMessage,
            @HeaderParam("Accept-Language") String acceptLanguage
    ) {
        ClaimSubmissionProgress progress = repository.findBySessionId(sessionId);
        ClaimSubmissionStep currentStep = null;
        for (ClaimSubmissionStep step : ClaimSubmissionStep.values()) {
            if (!step.isComplete(progress)) {
                currentStep = step;
                break;
            }
        }
        stepParseMessageTool.parseStepMessage(currentStep, userChatMessage, sessionId);
        String chatResponse = stepMessageTool.getStepMessage(sessionId, currentStep, acceptLanguage);
        ChatResponseDto responseDto = new ChatResponseDto(
                sessionId,
                chatResponse,
                repository
        );
        return Response.ok(responseDto).build();
    }

}
