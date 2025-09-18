package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.application.tool.WelcomeTool;
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

@jakarta.ws.rs.Path("/v1/api/fnol")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class FnolResource {

    @Inject
    FNOLAssistantAgent agent;
    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    GuardrailsContext guardrailsContext;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;
    @Inject
    WelcomeTool welcomeTool;

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

    @POST
    @jakarta.ws.rs.Path("/chat")
    @jakarta.enterprise.context.control.ActivateRequestContext
    public Response chat(@BeanParam ChatForm form, @HeaderParam("Accept-Language") String acceptLanguage) throws Exception {

        if (form == null || form.userMessage == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"userMessage obbligatorio\"}").build();
        }

        // Usa quello del form oppure genera
        String sessionId;
        if (form.sessionId == null || form.sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            try {
                return Response.ok(welcomeTool.welcomeMsg(form.policyNumber, form.emailAddress, acceptLanguage, sessionId)).build();
            } catch (BadRequestException e) {
                //todo gestione errori con @ExceptionHandler
                return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
            }
        } else {
            sessionId = form.sessionId;
        }

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
                        String.join("\n", paths) +
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
                userMessage += "[AUDIO_MESSAGE]\n" + dst + "\n[/AUDIO_MESSAGE]";
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
