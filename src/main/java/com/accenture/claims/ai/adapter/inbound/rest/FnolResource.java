package com.accenture.claims.ai.adapter.inbound.rest;

import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
    public Response chat(@BeanParam ChatForm form) {

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

        String answer = agent.chat(sessionId, userMessage);
        return Response.ok(new ChatResponseDto(sessionId, answer)).build();
    }
}
