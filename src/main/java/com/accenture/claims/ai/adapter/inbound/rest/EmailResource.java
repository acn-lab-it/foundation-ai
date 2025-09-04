package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.dto.email.DownloadedAttachment;
import com.accenture.claims.ai.application.service.EmailService;
import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import com.accenture.claims.ai.domain.model.emailParsing.Reporter;
import com.accenture.claims.ai.domain.repository.EmailParsingResultRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;

import java.util.Optional;

@jakarta.ws.rs.Path("/api/emails")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmailResource {

    @Inject
    EmailService emailService;

    @Inject
    EmailParsingResultRepository emailParsingResultRepository;


    @GET
    public Response findAll(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        try {
            return Response.ok(emailService.findAll(page, size)).build();
        } catch (Exception e) {
            //TODO: gestione errori
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/{id}")
    public Response findOne(@PathParam("id") String id) {
        try {
            return Response.ok(emailService.findOne(id)).build();
        } catch (Exception e) {
            //TODO: gestione errori
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/{emailId}/attachments/{filename}")
    public Response downloadAttachment(@PathParam("emailId") String emailId, @PathParam("filename") String filename) {
        try {
            DownloadedAttachment downloaded = emailService.downloadAttachment(emailId, filename);

            return Response.ok(downloaded.getContent())
                    .type(downloaded.getContentType())
                    .header("Content-Disposition", downloaded.getContentDisposition())
                    .build();
        } catch (Exception e) {
            //TODO: gestione errori
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PUT
    @Path("/{emailId}/fillInfo")
    public Response fillInfo(@PathParam("emailId") String emailId, EmailParsingResult input) {
        Optional<EmailParsingResult> found = emailParsingResultRepository.findByEmailId(emailId);
        if (found.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        EmailParsingResult result = found.get();
        if (result.getReporter() == null) {
            result.setReporter(new Reporter());
        }
        if (input.getReporter() != null) {
            if (StringUtils.isBlank(result.getReporter().getFirstName()) && !StringUtils.isBlank(input.getReporter().getFirstName())) {
                result.getReporter().setFirstName(input.getReporter().getFirstName());
            }
            if (StringUtils.isBlank(result.getReporter().getLastName()) && !StringUtils.isBlank(input.getReporter().getLastName())) {
                result.getReporter().setLastName(input.getReporter().getLastName());
            }
        }
        if (StringUtils.isBlank(result.getIncidentLocation()) && !StringUtils.isBlank(input.getIncidentLocation())) {
            result.setIncidentLocation(input.getIncidentLocation());
        }
        if (StringUtils.isBlank(result.getPolicyNumber()) && !StringUtils.isBlank(input.getPolicyNumber())) {
            result.setPolicyNumber(input.getPolicyNumber());
        }
        if (StringUtils.isBlank(result.getIncidentDate()) && !StringUtils.isBlank(input.getIncidentDate())) {
            result.setIncidentDate(input.getIncidentDate());
        }

        if (StringUtils.isBlank(result.getReporter().getFirstName()) ||
                StringUtils.isBlank(result.getReporter().getLastName()) ||
                StringUtils.isBlank(result.getIncidentLocation()) ||
                StringUtils.isBlank(result.getPolicyNumber()) ||
                StringUtils.isBlank(result.getIncidentDate())) {

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing mandatory fields")
                    .build();
        }

        emailParsingResultRepository.update(result);

        return Response.ok().build();
    }
}
