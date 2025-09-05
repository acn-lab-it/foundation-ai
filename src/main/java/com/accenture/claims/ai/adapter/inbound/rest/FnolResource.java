package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.AttachmentDto;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.DownloadedAttachment;
import com.accenture.claims.ai.adapter.inbound.rest.dto.email.EmailDto;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.emailFlow.EmailMediaAgent;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.application.agent.emailFlow.FNOLEmailAssistantAgent;
import com.accenture.claims.ai.application.service.EmailService;
import com.accenture.claims.ai.application.tool.emailFlow.DraftMissingInfoEmailTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@jakarta.ws.rs.Path("/fnol")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class FnolResource {

    @Inject
    FNOLAssistantAgent agent;
    @Inject
    FNOLEmailAssistantAgent emailAgent;
    @Inject
    EmailMediaAgent mediaAgent;
    @Inject
    DraftMissingInfoEmailTool draftMissingInfoEmailTool;
    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    GuardrailsContext guardrailsContext;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;
    @Inject
    EmailService emailService;

    private static final ObjectMapper M = new ObjectMapper();

    public static class ChatResponseDto {
        public String sessionId;
        public String answer;
        public Object finalResult; // null se non è stato creato

        public ChatResponseDto(String sessionId, String answer, Object finalResult) {
            this.sessionId = sessionId;
            this.answer = answer;
            this.finalResult = finalResult;
        }
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

    @POST
    @jakarta.ws.rs.Path("/chat")
    @jakarta.enterprise.context.control.ActivateRequestContext
    public Response chat(@BeanParam ChatForm form, @HeaderParam("Accept-Language") String acceptLanguage) throws Exception {

        if (form == null || form.userMessage == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"userMessage obbligatorio\"}").build();
        }

        // Usa quello del form oppure genera
        String sessionId = (form.sessionId == null || form.sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : form.sessionId;

        String userMessage = form.userMessage;

        if (userMessage.toLowerCase().contains("claim summary")){
            var fo = finalOutputJSONStore.get("final_output", sessionId);
            ObjectNode complete = buildComplete(finalOutputJSONStore, sessionId, mockJson);
            System.out.println("========== CURRENT MOCKED_OUTPUT ==========");
            System.out.println(fo == null ? "<empty>" : complete.toPrettyString());
            System.out.println("===========================================\n");
            return Response.ok(new ChatResponseDto(sessionId, "Here is the final claim Summary. Thank you for using our service.", complete)).build();
        }

        // Gestione eventuali file
        if (form.files != null && !form.files.isEmpty()) {
            try {
                Path tmpDir = Files.createTempDirectory("chat-media-");
                List<String> paths = new ArrayList<>();
                for (FileUpload fu : form.files) {
                    Path dst = tmpDir.resolve(fu.fileName());
                    Files.copy(fu.uploadedFile(), dst);
                    paths.add(dst.toString());
                }
                userMessage += "\n\n[MEDIA_FILES]\n" +
                        paths.stream().collect(Collectors.joining("\n")) +
                        "\n[/MEDIA_FILES]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"upload_failure\"}")
                        .build();
            }
        }

        // Gestione messaggio vocale
        if (form.userAudioMessage != null) {
            try {
                Path tmpDir = Files.createTempDirectory("chat-audio-");
                Path dst = tmpDir.resolve(form.userAudioMessage.fileName());
                Files.copy(form.userAudioMessage.uploadedFile(), dst);

                // Se c'è un messaggio vocale, ignora il messaggio testuale dell'utente
                // e usa solo il messaggio vocale
                userMessage += "[AUDIO_MESSAGE]\n" + dst.toString() + "\n[/AUDIO_MESSAGE]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"audio_upload_failure\"}")
                        .build();
            }
        }

        // Recupero la lingua e il main prompt del superagent (fallback su "en" se non gestiamo la lingua richiesta)
        LanguageHelper.PromptResult promptResult =
                languageHelper.getPromptWithLanguage(acceptLanguage, "superAgent.mainPrompt");

        // Inietto la sessionId corrente nel prompt per renderlo aware del vero sessionID (scopo: evitare confusioni nelle chiamate interne)
        String systemPrompt = languageHelper.applyVariables(promptResult.prompt, Map.of("sessionId", sessionId));

        // Imposto la lingua di sessione per i sotto-prompt e pro-futuro
        sessionLanguageContext.setLanguage(sessionId, promptResult.language);

        // Imposto il contesto per i guardrails
        guardrailsContext.setSessionId(sessionId);
        guardrailsContext.setSystemPrompt(systemPrompt);

        String raw = agent.chat(sessionId, systemPrompt, userMessage);

        System.out.println("=================== Agent Response =======================");
        System.out.println("RAW: " + raw);
        System.out.println("==========================================================\n");
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return Response.serverError().build();
        }

        ChatResponseDto dto;
        try {
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(raw);
            String answer = node.has("answer") ? node.get("answer").asText() : raw;
            Object finalResult = node.has("finalResult") && !node.get("finalResult").isNull()
                    ? mapper.convertValue(node.get("finalResult"), Object.class)
                    : null;
            var fo = finalOutputJSONStore.get("final_output", sessionId);
            System.out.println("========== CURRENT FINAL_OUTPUT ==========");
            System.out.println(fo == null ? "<empty>" : fo.toPrettyString());
            System.out.println("==========================================\n");
            dto = new ChatResponseDto(sessionId, answer, fo);
        } catch (Exception ex) {
            // Se il modello non segue lo schema, fallback
            dto = new ChatResponseDto(sessionId, raw, null);
        }

        return Response.ok(dto).build();
    }

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

        Optional<MissingResponseDto> maybeMissing = computeMissingPayload(sessionId, emailId, sender);
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

        ChatResponseDto dto;
        try {
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(rawEmailData);
            String answer = "";
            Object finalResult = node.has("finalResult") && !node.get("finalResult").isNull()
                    ? mapper.convertValue(node.get("finalResult"), Object.class)
                    : null;

            var fo = finalOutputJSONStore.get("email_parsing_result", sessionId);
            System.out.println("========== CURRENT FINAL_OUTPUT ==========");
            System.out.println(fo == null ? "<empty>" : fo.toPrettyString());
            System.out.println("==========================================\n");

            dto = new ChatResponseDto(sessionId, answer, fo);
        } catch (Exception ex) {
            dto = new ChatResponseDto(sessionId, rawEmailData, null);
        }

        return Response.ok(dto).build();
    }


    /** merge ricorsivo “in avanti”: copia SOLO i campi mancanti */
    private static void fillMissing(ObjectNode target, ObjectNode defaults) {
        defaults.fields().forEachRemaining(e -> {
            String k = e.getKey();
            JsonNode defVal = e.getValue();

            if (!target.has(k) || target.get(k).isNull()) {
                // campo assente → copia default (deep copy se oggetto / array)
                target.set(k, defVal.deepCopy());
            } else if (defVal.isObject() && target.get(k).isObject()) {
                // entrambi object → ricorri
                fillMissing((ObjectNode) target.get(k), (ObjectNode) defVal);
            }
            // se esiste già un valore NON nullo lo lasciamo com’è
        });
    }

    /** restituisce un ObjectNode COMPLETO */
    public static ObjectNode buildComplete(
            FinalOutputJSONStore store, String sessionId, String mockJson) throws Exception {

        // 1. JSON mock di riferimento
        ObjectNode mock = (ObjectNode) M.readTree(mockJson);

        // 2. JSON parziale dal DB (vuoto se non esiste)
        ObjectNode fo = store.get("final_output", sessionId);

        // 3. copia profonda di fo, poi riempi i “buchi” con i default
        ObjectNode complete = fo.deepCopy();
        fillMissing(complete, mock);

        return complete;
    }

    private static String extractEmailAddress(String from) {
        if (from == null) return null;
        // prova match di un indirizzo email
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(from);
        return m.find() ? m.group() : from; // se non trova, restituisci l'originale
    }

    private static final Set<String> IMG_EXT = Set.of(
            "jpg","jpeg","png","gif","bmp","webp","tif","tiff","heic","heif","svg"
    );
    private static final Set<String> VID_EXT = Set.of(
            "mp4","mov","m4v","avi","mkv","webm","mpeg","mpg","3gp","3gpp","wmv"
    );
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

    private Optional<MissingResponseDto> computeMissingPayload(String sessionId, String emailId, String sender) throws Exception {
        // 1) tenta per (sessionId,emailId)
        ObjectNode current = finalOutputJSONStore.get("email_parsing_result", sessionId, emailId);

        // 2) Fallback: email_parsing_result solo per sessionId (primo step potrebbe aver salvato senza emailId)
        if (current == null || current.isEmpty()) {
            current = finalOutputJSONStore.get("email_parsing_result", sessionId);
        }

        ObjectNode missing = M.createObjectNode();

        // campi obbligatori
        if (isBlank(current.path("policyNumber")))     missing.putNull("policyNumber");
        if (isBlank(current.path("incidentDate")))     missing.putNull("incidentDate");
        if (isBlank(current.path("incidentLocation"))) missing.putNull("incidentLocation");

        JsonNode reporter = current.path("reporter");
        if (isBlank(reporter.path("name")))            missing.putNull("reporter.name");
        if (isBlank(reporter.path("surname")))         missing.putNull("reporter.surname");

        // whatHappened*: se ENTRAMBI mancanti ⇒ NON chiederli puntualmente, ma solo la dinamica
        boolean missingWHC    = isBlank(current.path("whatHappenedCode"));
        boolean missingWHCtx  = isBlank(current.path("whatHappenedContext"));
        boolean needMoreAccidentDetails = (missingWHC && missingWHCtx);

        // Se solo uno dei due manca, puoi decidere se chiedere quello specifico.

        boolean hasMissing = missing.size() > 0 || needMoreAccidentDetails;
        if (!hasMissing) return Optional.empty();

        String recipientEmail = current.path("reporter").path("contacts").path("email").asText(null);
        if (recipientEmail == null || recipientEmail.isBlank()) recipientEmail = sender;

        String locale = sessionLanguageContext.getLanguage(sessionId);

        System.out.println("========== CURRENT EMAIL_PARSING_RESULT ==========");
        System.out.println(sessionId + " " + emailId);
        System.out.println(current == null ? "<empty>" : current.toPrettyString());
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

        return Optional.of(new MissingResponseDto(
                sessionId,
                emailBody,
                current.deepCopy()
        ));
    }

    // utility
    private static boolean isBlank(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return true;
        if (n.isTextual()) return n.asText().trim().isEmpty();
        return false;
    }

    String mockJson = """
        {
          "_id": "1e33bfb4-1ea8-4e05-9090-1d6495f52f37",
          "policyNumber": "AUTHHR00026397",
          "policyStatus": "ACTIVE",
          "reporter": {
            "firstName": "Lukas",
            "lastName": "Baumgartner",
            "contacts": {
              "email": "allianz@test.at",
              "mobile": "+61456677674"
            }
          },
          "incidentDate": "2025-07-27T14:30:00Z",
          "whatHappenedCode": "NM_FIRE",
          "whatHappenedContext": "Fire (excl. Bushfire and Grassfire)",
          "incidentLocation": "Linzer Str. 225, Vienna 1010",
          "imagesUploaded": [
            {
              "mediaName": "burnt-american-fridge-after-kitchen-house-fire-BMA3DX.jpg",
              "mediaDescription": "PROPERTY - Kitchen",
              "mediaType": "image"
            }
          ],
          "circumstances": {
            "details": "Fire or other events",
            "notes": "While the customer was cooking, a fire broke out in the kitchen. The incident occurred on 27 July 2025 at 2:30 p.m. at Linzer Str. 225, 1010 Vienna."
          },
          "damageDetails": "PROPERTY ‑ Kitchen (conf. 0.95)",
          "administrativeCheck": {
            "passed": true
          }
        }
        """;

}
