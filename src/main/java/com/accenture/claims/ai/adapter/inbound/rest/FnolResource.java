package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;

@jakarta.ws.rs.Path("/fnol")
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
    FinalOutputStore finalOutputStore;

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
    public Response chat(@BeanParam ChatForm form, @HeaderParam("Accept-Language") String acceptLanguage) {

        if (form == null || form.userMessage == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"userMessage obbligatorio\"}").build();
        }

        // Usa quello del form oppure genera
        String sessionId = (form.sessionId == null || form.sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : form.sessionId;

        String userMessage = form.userMessage;

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
            // fallback lato server giusto per non restituire mai un body vuoto al client
            raw = """
              {"answer":"Something went wrong. Try Again."}
            """;
        }

        ChatResponseDto dto;
        try {
            ObjectMapper mapper = new ObjectMapper();
            var node = mapper.readTree(raw);
            String answer = node.has("answer") ? node.get("answer").asText() : raw;
            Object finalResult = node.has("finalResult") && !node.get("finalResult").isNull()
                    ? mapper.convertValue(node.get("finalResult"), Object.class)
                    : null;
            var fo = finalOutputStore.get(sessionId);
            System.out.println("========== CURRENT FINAL_OUTPUT ==========");
            System.out.println(fo == null ? "<empty>" : fo.toPrettyString());
            System.out.println("==========================================\n");
            dto = new ChatResponseDto(sessionId, answer, finalResult);
        } catch (Exception ex) {
            // Se il modello non segue lo schema, fallback
            dto = new ChatResponseDto(sessionId, raw, null);
        }

        return Response.ok(dto).build();
    }
}
