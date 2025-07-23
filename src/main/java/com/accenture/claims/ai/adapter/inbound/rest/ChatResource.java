/*
package com.accenture.claims.ai.api;

import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ImageSource;
import com.accenture.claims.ai.application.agent.FNOLAssistantAgent;
import com.accenture.claims.ai.application.agent.MediaOcrAgent;
import com.accenture.claims.ai.application.agent.SuperAgent;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@jakarta.ws.rs.Path("/chat")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class ChatResource {

    private final MediaOcrAgent ocr;
    private final SuperAgent assistant;

    public ChatResource(MediaOcrAgent ocr, SuperAgent assistant) {
        this.ocr = ocr;
        this.assistant = assistant;
    }


    @POST
    public Response chat(@BeanParam ChatForm form) {

        try {
 //Saalva gli eventuali allegati su /tmp e prendi i Path

            List<Path> paths = new ArrayList<>();
            if (form.files != null && !form.files.isEmpty()) {
                Path tmpDir = Files.createTempDirectory("chat-");
                for (FileUpload fu : form.files) {
                    Path dst = tmpDir.resolve(fu.fileName());
                    Files.copy(fu.uploadedFile(), dst);
                    paths.add(dst);
                }
            }

 //Esegui OCR solo se sono stati caricati media

            String visionJson = "";
            if (!paths.isEmpty()) {
                List<ImageSource> src = paths.stream()
                        .map(p -> new ImageSource(p.toString()))
                        .collect(Collectors.toList());
                visionJson = ocr.runOcr(src, form.userMessage == null ? "" : form.userMessage);
            }

 //Riprendi flusso LLM

            String userMsg = form.userMessage == null ? "" : form.userMessage;


            String answer  = assistant.chat(userMsg, visionJson);

 //Ritorna la risposta

            return Response.ok(answer).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError()
                    .entity("{\"error\":\"internal_failure\"}")
                    .build();
        }
    }
}
*/
