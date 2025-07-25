package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
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

    public static class ChatResponseDto {
        public String sessionId;
        public String answer;
        public ChatResponseDto(String sessionId, String answer) {
            this.sessionId = sessionId;
            this.answer = answer;
        }
    }

    @POST
    @jakarta.ws.rs.Path("/chat")
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

        // Recupero la lingua e il main prompt del superagent (fallback su "en" se non gestiamo la lingua richiesta)
        LanguageHelper.PromptResult promptResult = LanguageHelper.getPromptWithLanguage(acceptLanguage, "superAgent.mainPrompt");

        // Inietto la sessionId corrente nel prompt per renderlo aware del vero sessionID (scopo: evitare confusioni nelle chiamate interne)
        String systemPrompt = promptResult.prompt.replace("{{sessionId}}", sessionId);

        // Imposto la lingua di sessione per i sotto-prompt e pro-futuro
        sessionLanguageContext.setLanguage(sessionId, promptResult.language);

        String answer = agent.chat(sessionId, systemPrompt, userMessage);
        return Response.ok(new ChatResponseDto(sessionId, answer)).build();
    }
}
