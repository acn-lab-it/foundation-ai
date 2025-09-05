package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.emailFlow.FNOLEmailAssistantAgent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.UUID;

@Path("/fnol/email")
@ApplicationScoped
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class EmailTestController {

    @Inject
    FNOLEmailAssistantAgent agent;

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    LanguageHelper languageHelper;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /* ===== DTO ===== */

    public static final class FnolEmailRequest {
        public String from;
        public String mailMessage;
        public List<FileUpload> files;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Contacts {
        public String email;
        public String mobile;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class Reporter {
        public String name;
        public String surname;
        public Contacts contacts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class FnolEmailExtraction {
        public String policyNumber;
        public String incidentDate;      // ISO 8601 o null
        public String incidentLocation;  // <-- adesso a root
        public Reporter reporter;
    }

    /* ===== Endpoint (multipart) ===== */
    @POST
    @ActivateRequestContext
    public Response extractMultipart(
            @RestForm("from") String from,
            @RestForm("mailMessage") String mailMessage,
       //     @RestForm("files") @PartType("application/octet-stream") List<FileUpload> files,
            @HeaderParam("Accept-Language") String acceptLanguage

    ) {
        FnolEmailRequest req = new FnolEmailRequest();
        req.from = from;
        req.mailMessage = mailMessage;
        //req.files = files;
        return doExtract(req);
    }

    /* ===== Logica comune ===== */
    private Response doExtract(FnolEmailRequest req) {
        if (req == null || isBlank(req.mailMessage) || isBlank(req.from)) {
            return Response.ok(nulls()).build();
        }

        String sys = """
            You work for an insurance company. You are an information extractor. Read the email content and extract the appropriate JSON.
            Output MUST be a single JSON object EXACTLY like provided by the tool.
            If a field is missing/unknown/invalid, set it to null.
            Do not invent values. Do not add/remove/rename fields. No comments. No code fences.
            If you receive an email message use the EmailParserTool.parseEmail to convert it to a JSON object.
            """;

        String user = """
            Sender (from): %s

            Email body:
            --------------------
            %s
            --------------------
            Return ONLY the JSON object.
            """.formatted(safe(req.from), safe(req.mailMessage));

        String sessionId = UUID.randomUUID().toString();

        String raw = agent.chat(sessionId, sys, user);

        return Response.ok(raw).build();


    }

    /* ===== Helpers ===== */
    private static String safe(String s) { return s == null ? "" : s; }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static FnolEmailExtraction nulls() {
        FnolEmailExtraction o = new FnolEmailExtraction();
        o.policyNumber = null;
        o.incidentDate = null;
        o.incidentLocation = null;  // <-- root
        o.reporter = new Reporter();
        o.reporter.name = null;
        o.reporter.surname = null;
        o.reporter.contacts = new Contacts();
        o.reporter.contacts.email = null;
        o.reporter.contacts.mobile = null;
        return o;
    }
}
 