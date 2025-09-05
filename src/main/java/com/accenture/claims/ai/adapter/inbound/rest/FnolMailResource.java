package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.AttachmentDto;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.DownloadedAttachment;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.EmailDto;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.emailFlow.EmailMediaAgent;
import com.accenture.claims.ai.application.agent.emailFlow.FNOLEmailAssistantAgent;
import com.accenture.claims.ai.application.service.EmailService;
import com.accenture.claims.ai.application.tool.emailFlow.DraftMissingInfoEmailTool;
import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import com.accenture.claims.ai.domain.model.emailParsing.Reporter;
import com.accenture.claims.ai.domain.repository.EmailParsingResultRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@jakarta.ws.rs.Path("/fnol")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class FnolMailResource {

    @Inject
    FNOLEmailAssistantAgent emailAgent;
    @Inject
    EmailMediaAgent mediaAgent;
    @Inject
    EmailService emailService;
    @Inject
    DraftMissingInfoEmailTool draftMissingInfoEmailTool;
    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    GuardrailsContext guardrailsContext;
    @Inject
    EmailParsingResultRepository   emailParsingResultRepository;

    private static final ObjectMapper M = new ObjectMapper();
    private static final Set<String> IMG_EXT = Set.of(
            "jpg","jpeg","png","gif","bmp","webp","tif","tiff","heic","heif","svg"
    );
    private static final Set<String> VID_EXT = Set.of(
            "mp4","mov","m4v","avi","mkv","webm","mpeg","mpg","3gp","3gpp","wmv"
    );

    @POST
    @jakarta.ws.rs.Path("/parseEmail/{emailId}")
    @jakarta.enterprise.context.control.ActivateRequestContext
    public Response parseEmail(@PathParam("emailId") String emailId,
                               @HeaderParam("Accept-Language") String acceptLanguage) throws Exception {

        if (emailId == null || emailId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"emailId obbligatorio\"}").build();
        }

        String sessionId = UUID.randomUUID().toString();

        EmailDto email = emailService.findOne(emailId);
        if (email == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String userMessage = email.getText();
        String sender = extractEmailAddress(email.getFrom());

        List<String> mediaPaths = new ArrayList<>();

        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            try {
                Path tmpDir = Files.createTempDirectory("email-media-" + emailId);
                for (AttachmentDto att : email.getAttachments().values()) {

                    boolean isMedia = false;
                    try {
                        String ct = att.getContentType();
                        isMedia = isMediaContentType(ct);
                    } catch (Throwable ignore) { }
                    if (!isMedia) {
                        isMedia = isMediaFileName(att.getFilename());
                    }
                    if (!isMedia) {
                        continue;
                    }

                    DownloadedAttachment attachment = emailService.downloadAttachment(emailId, att.getFilename());
                    Path dst = tmpDir.resolve(att.getFilename());
                    Files.write(dst, attachment.getContent());
                    mediaPaths.add(dst.toString());
                }

            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"upload_failure\"}")
                        .build();
            }
        }

        LanguageHelper.PromptResult emailPromptResult =
                languageHelper.getPromptWithLanguage(acceptLanguage, "emailAgent.mainPrompt");

        String emailSystemPrompt = languageHelper.applyVariables(
                emailPromptResult.prompt,
                Map.of(
                        "sessionId", sessionId,
                        "emailId", emailId,
                        "emailSender", sender,
                        "currentDate", new Date().toString()
                )
        );

        sessionLanguageContext.setLanguage(sessionId, emailPromptResult.language);
        guardrailsContext.setSessionId(sessionId);
        guardrailsContext.setSystemPrompt(emailSystemPrompt);

        String rawEmailData = emailAgent.chat(sessionId, emailSystemPrompt, userMessage);

        Optional<FnolResource.MissingResponseDto> maybeMissing = computeMissingPayload(sessionId, emailId, sender);
        if (maybeMissing.isPresent()) {
            System.out.println("=================== MISSING INFO AGENT Response =======================");
            System.out.println("RAW: " + maybeMissing.get().emailBody);
            System.out.println("FinalResult: " + maybeMissing.get().finalResult);
            return Response.status(422).entity(maybeMissing.get()).build();
        }

        // ===== Invocazione MEDIA AGENT se ci sono media =====
        if (!mediaPaths.isEmpty()) {
            userMessage += "\n\n[MEDIA_FILES]\n" + String.join("\n", mediaPaths) + "\n[/MEDIA_FILES]";
            LanguageHelper.PromptResult mediaPromptResult =
                    languageHelper.getPromptWithLanguage(acceptLanguage, "emailAgent.mediaAgent");

            String mediaSystemPrompt = languageHelper.applyVariables(
                    mediaPromptResult.prompt,
                    Map.of(
                            "sessionId", sessionId,
                            "emailId", emailId,
                            "currentDate", new Date().toString()
                    )
            );

            // Costruisco un user message minimale per il media agent: BODY + blocco MEDIA_FILES
            String mediaUserMessage = """
                BODY:
                --------------------
                %s

                [MEDIA_FILES]
                %s
                [/MEDIA_FILES]
                --------------------
                """.formatted(userMessage, String.join("\n", mediaPaths));

            // Allineo lingua e guardrails anche per il secondo agent (stessa sessione)
            sessionLanguageContext.setLanguage(sessionId, mediaPromptResult.language);
            guardrailsContext.setSessionId(sessionId);
            guardrailsContext.setSystemPrompt(mediaSystemPrompt);

            String rawMedia = mediaAgent.chat(sessionId, mediaSystemPrompt, mediaUserMessage);

            System.out.println("=================== Media Agent Response =======================");
            System.out.println("RAW: " + rawMedia);
            System.out.println("================================================================\n");
        }

        FnolResource.ChatResponseDto dto;
        try {
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(rawEmailData);
            String answer = "";
            Object finalResult = node.has("finalResult") && !node.get("finalResult").isNull()
                    ? mapper.convertValue(node.get("finalResult"), Object.class)
                    : null;

            var fo = emailParsingResultRepository.findBySessionId(sessionId);
            System.out.println("========== CURRENT FINAL_OUTPUT ==========");
            System.out.println(fo == null ? "<empty>" : fo.toString());
            System.out.println("==========================================\n");

            dto = new FnolResource.ChatResponseDto(sessionId, answer, fo);
        } catch (Exception ex) {
            dto = new FnolResource.ChatResponseDto(sessionId, rawEmailData, null);
        }

        return Response.ok(dto).build();
    }

    public static class MissingResponseDto {
        public String sessionId;
        public String emailBody;
        public Object finalResult;
        public MissingResponseDto(String sessionId, String emailBody, Object finalResult) {
            this.sessionId = sessionId;
            this.emailBody = emailBody;
            this.finalResult = finalResult;
        }
    }
    
    private static String extractEmailAddress(String from) {
        if (from == null) return null;
        // prova match di un indirizzo email
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(from);
        return m.find() ? m.group() : from; // se non trova, restituisci l'originale
    }
    private static boolean isMediaContentType(String ct) {
        if (ct == null) return false;
        String c = ct.toLowerCase(Locale.ITALY);
        return c.startsWith("image/") || c.startsWith("video/");
    }

    private static boolean isMediaFileName(String fileName) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return false;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ITALY);
        return IMG_EXT.contains(ext) || VID_EXT.contains(ext);
    }

    private Optional<FnolResource.MissingResponseDto> computeMissingPayload(String sessionId, String emailId, String sender) throws Exception {
        // 1) tenta per (sessionId,emailId)
        Optional<EmailParsingResult> optCurrent = emailParsingResultRepository.findByEmailIdAndSessionId(emailId, sessionId);
        if (optCurrent.isEmpty()) {
            // 2) Fallback: email_parsing_result solo per sessionId (primo step potrebbe aver salvato senza emailId)
            //FIXME: ci serve davvero? non possiamo trovare direttamente per session id?
            optCurrent = emailParsingResultRepository.findBySessionId(sessionId);
            if (optCurrent.isEmpty()) {
                throw new RuntimeException("JSON NON PRESENTE");
            }
        }
        EmailParsingResult current = optCurrent.get();

        ObjectNode missing = M.createObjectNode();

        // campi obbligatori
        if (StringUtils.isBlank(current.getPolicyNumber()))     missing.putNull("policyNumber");
        if (StringUtils.isBlank(current.getIncidentDate()))      missing.putNull("incidentDate");
        if (StringUtils.isBlank(current.getIncidentLocation()))  missing.putNull("incidentLocation");

        Reporter reporter = current.getReporter();
        if (reporter == null) {
            missing.putNull("reporter.firstName");
            missing.putNull("reporter.lastName");
        } else {
            if (StringUtils.isBlank(reporter.getFirstName()))  missing.putNull("reporter.firstName");
            if (StringUtils.isBlank(reporter.getLastName()))  missing.putNull("reporter.LastName");
        }

        // whatHappened*: se ENTRAMBI mancanti ⇒ NON chiederli puntualmente, ma solo la dinamica
        boolean missingWHC    = StringUtils.isBlank(current.getWhatHappenedCode());
        boolean missingWHCtx  = StringUtils.isBlank(current.getWhatHappenedContext());
        boolean needMoreAccidentDetails = (missingWHC && missingWHCtx);

        // Se solo uno dei due manca, puoi decidere se chiedere quello specifico.

        boolean hasMissing = missing.size() > 0 || needMoreAccidentDetails;
        if (!hasMissing) return Optional.empty();

        String recipientEmail = null;
        if (reporter != null && reporter.getContacts() != null && !StringUtils.isBlank(reporter.getContacts().getEmail())) {
            recipientEmail = reporter.getContacts().getEmail();
        }
        if (recipientEmail == null || recipientEmail.isBlank()) recipientEmail = sender;

        String locale = sessionLanguageContext.getLanguage(sessionId);

        System.out.println("========== CURRENT EMAIL_PARSING_RESULT ==========");
        System.out.println(sessionId + " " + emailId);
        System.out.println(current == null ? "<empty>" : current.toString());
        System.out.println(missing.toPrettyString());
        System.out.println("==========================================\n");

        // genera la mail
        String emailBody = draftMissingInfoEmailTool.draftMissingInfoEmail(
                sessionId,
                emailId,
                recipientEmail,
                missing.toString(),
                needMoreAccidentDetails,
                locale
        );

        return Optional.of(new FnolResource.MissingResponseDto(
                sessionId,
                emailBody,
                current
        ));
    }

    // utility
    private static boolean isBlank(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return true;
        if (n.isTextual()) return n.asText().trim().isEmpty();
        return false;
    }

}
