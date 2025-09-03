package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.dto.email.DownloadedAttachment;
import com.accenture.claims.ai.application.service.EmailService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api/emails")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmailResource {

    @Inject
    EmailService emailService;


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
}
