package com.accenture.claims.ai;

import com.accenture.claims.ai.dto.CoverageResponse;
import com.accenture.claims.ai.dto.UserPrompt;
import com.accenture.claims.ai.repository.AdminCoverageRepository;
import com.accenture.claims.ai.repository.TechCoverageRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

@Path("/api")
public class CoverageResource {

    @Inject
    CoverageCheckAgent coverageCheckAgent;

    @Inject
    AdminCoverageRepository adminCoverageRepository;

    @Inject
    TechCoverageRepository techCoverageRepository;

    @POST
    @Path("/coverage-check")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response checkCoverage(UserPrompt userPrompt) {
        UserInput userInput = coverageCheckAgent.detectUserInput(userPrompt.getMessage());
        Decision decision = coverageCheckAgent.detectCoverage(userPrompt.getMessage());
        List<CoverageResponse> results = new ArrayList<>();
        var techCoverageResponse = new CoverageResponse();
        var adminCoverageResponse = new CoverageResponse();
        switch (decision.action) {
            case TECHNICAL:
                techCoverageResponse.setResponse(techCoverageRepository.techCoverageCheck(userInput.getPolicyNumber(), userInput.getWhc()));
                techCoverageResponse.setCoverageType(Decision.Action.TECHNICAL.name());
                techCoverageResponse.setPolicyNumber(userInput.getPolicyNumber());
                results.add(techCoverageResponse);
                break;
            case ADMIN:
                adminCoverageResponse.setResponse(adminCoverageRepository.adminCoverageCheck(userInput.getPolicyNumber(), userInput.getWhc()));
                adminCoverageResponse.setCoverageType(Decision.Action.ADMIN.name());
                adminCoverageResponse.setPolicyNumber(userInput.getPolicyNumber());
                results.add(adminCoverageResponse);
                break;
            case BOTH:
                techCoverageResponse.setResponse(techCoverageRepository.techCoverageCheck(userInput.getPolicyNumber(), userInput.getWhc()));
                techCoverageResponse.setCoverageType(Decision.Action.TECHNICAL.name());
                techCoverageResponse.setPolicyNumber(userInput.getPolicyNumber());
                adminCoverageResponse.setResponse(adminCoverageRepository.adminCoverageCheck(userInput.getPolicyNumber(), userInput.getWhc()));
                adminCoverageResponse.setCoverageType(Decision.Action.ADMIN.name());
                adminCoverageResponse.setPolicyNumber(userInput.getPolicyNumber());
                results.add(adminCoverageResponse);
                results.add(techCoverageResponse);
                break;
            case NONE:
                break;
        }
        return Response.ok(results).build();
    }


}
