package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.chat.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ImageSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class ChatV2MediaOcrAgentV2 {

    @Inject
    ChatModel visionModel;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private static final ObjectMapper M = new ObjectMapper();
    private static final String DEFAULT_JSON_FALLBACK = """
    {
      "damageCategory": "NONE",
      "damagedEntity": "NONE",
      "eventType": "NONE",
      "propertyCode": "NONE",
      "claimDate": "NONE",
      "claimHour": "NONE",
      "claimProofDate": "NONE",
      "claimReceivedDate": "NONE",
      "ready": false,
      "confidence": 0
    }
    """;

    @Tool("Process media files for damage analysis")
    public String processMediaV2(String sessionId, String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "File not found: " + filePath;
            }

            List<ImageSource> sources = List.of(new ImageSource(filePath));
            return runOcr(sessionId, sources, "");

        } catch (Exception e) {
            return "Error processing media: " + e.getMessage();
        }
    }

    public String runOcr(String sessionId, List<ImageSource> sources, String userText) {
        try {
            List<Image> images = new ArrayList<>();
            int budgetLeft = 5; // MAX_TOTAL_IMAGES
            
            for (ImageSource s : sources) {
                Path path = Path.of(s.getRef());
                if (budgetLeft == 0) break;
                
                if (isVideo(path)) {
                    // Per ora gestiamo solo immagini
                    continue;
                } else {
                    try {
                        byte[] bytes = Files.readAllBytes(path);
                        String b64 = Base64.getEncoder().encodeToString(bytes);
                        images.add(Image.builder().url("data:image/jpeg;base64," + b64).build());
                        budgetLeft--;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            
            List<ChatMessage> prompt = buildPrompt(sessionId, images, userText);
            ChatResponse resp = visionModel.chat(ChatRequest.builder()
                    .messages(prompt)
                    .temperature(0.0)
                    .maxOutputTokens(1_024)
                    .build());
            String raw = resp.aiMessage().text();
            String json = extractJsonBlock(raw).orElse(DEFAULT_JSON_FALLBACK);

            /* 3) patch in final_output ------------------------------------ */
            try {
                JsonNode result = M.readTree(json);

                ArrayNode imagesArr = M.createArrayNode();
                for (ImageSource s : sources) {
                    ObjectNode m = M.createObjectNode();
                    Path filePath = Path.of(s.getRef());
                    String fileName = filePath.getFileName().toString();
                    String mimeType = getMimeType(filePath);
                    
                    m.put("mediaName", fileName);
                    // descrizione sintetica: <damageCategory> - <damagedEntity>
                    String descr = result.path("damageCategory").asText("UNKNOWN") +
                            " - " +
                            result.path("damagedEntity").asText("UNKNOWN");
                    m.put("mediaDescription", descr);
                    m.put("mediaType", mimeType);
                    imagesArr.add(m);
                }

                /* 3.b  circumstances + damageDetails */
                ObjectNode patch = M.createObjectNode();
                patch.set("imagesUploaded", imagesArr);

                ObjectNode circumstances = M.createObjectNode();
                circumstances.put("details",
                        result.path("eventType").asText("UNKNOWN"));
                circumstances.put("notes", safe(userText));
                patch.set("circumstances", circumstances);

                patch.put("damageDetails", descrFromResult(result));

                /* merge sul documento sessione */
                finalOutputJSONStore.put("final_output", sessionId, null, patch);

            } catch (Exception ex) {
                ex.printStackTrace();   // non bloccare: restituiamo comunque json fallback
            }
            return json;
            
        } catch (Exception e) {
            e.printStackTrace();
            return DEFAULT_JSON_FALLBACK;
        }
    }

    private List<ChatMessage> buildPrompt(String sessionId, List<Image> images, String userText) {
        SystemMessage sys = SystemMessage.from("""
        Analizza le immagini fornite per identificare il tipo di danno assicurativo.
        Questi sono i tipi di eventi che devi identificare:
        - Fire or other events
        - Water damage and Search & Repair expenses for rupture of water or gas pipes
        - Atmospheric events
        - Electrical phenomenon
        - Socio-political events, terrorism, vandalism
        - Accidental glass damage
        - Excess water consumption
        - Natural catastrophe
        - Veterinary expenses
        - Theft and damage caused by thieves

        Rispondi con un JSON:
        {
          "damageCategory": "VEHICLE | PROPERTY | NONE",
          "damagedEntity": "<short name or NONE>",
          "eventType": "<event type or NONE>",
          "confidence": <0.0-1.0>
        }
        """);
        
        UserMessage user = UserMessage.from("Analizza queste immagini: " + userText);
        
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(sys);
        messages.add(user);
        
        // Aggiungi le immagini
        for (Image img : images) {
            messages.add(new UserMessage(ImageContent.from(img.url())));
        }
        
        return messages;
    }

    private static String descrFromResult(JsonNode r) {
        return "%s ‑ %s (conf. %.2f)".formatted(
                r.path("damageCategory").asText("UNKNOWN"),
                r.path("damagedEntity").asText("UNKNOWN"),
                r.path("confidence").asDouble(0.0)
        );
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static boolean isVideo(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".mov") || fileName.endsWith(".avi");
    }

    private static Optional<String> extractJsonBlock(String text) {
        // Cerca un blocco JSON nel testo
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return Optional.of(text.substring(start, end + 1));
        }
        return Optional.empty();
    }

    private static String getMimeType(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString().toLowerCase();
            String extension = "";
            
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                extension = fileName.substring(lastDotIndex + 1);
            }
            
            // MIME types per immagini
            switch (extension) {
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "png":
                    return "image/png";
                case "gif":
                    return "image/gif";
                case "bmp":
                    return "image/bmp";
                case "webp":
                    return "image/webp";
                case "tif":
                case "tiff":
                    return "image/tiff";
                case "heic":
                    return "image/heic";
                case "heif":
                    return "image/heif";
                case "svg":
                    return "image/svg+xml";
                    
                // MIME types per video
                case "mp4":
                    return "video/mp4";
                case "mov":
                    return "video/quicktime";
                case "avi":
                    return "video/x-msvideo";
                case "mkv":
                    return "video/x-matroska";
                case "webm":
                    return "video/webm";
                case "mpeg":
                case "mpg":
                    return "video/mpeg";
                case "3gp":
                    return "video/3gpp";
                case "wmv":
                    return "video/x-ms-wmv";
                    
                default:
                    // Fallback: determina se è immagine o video basandosi sull'estensione
                    if (isImageExtension(extension)) {
                        return "image/" + extension;
                    } else if (isVideoExtension(extension)) {
                        return "video/" + extension;
                    } else {
                        return "application/octet-stream";
                    }
            }
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    private static boolean isImageExtension(String extension) {
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "heic", "heif", "svg")
                .contains(extension.toLowerCase());
    }

    private static boolean isVideoExtension(String extension) {
        return Set.of("mp4", "mov", "avi", "mkv", "webm", "mpeg", "mpg", "3gp", "wmv")
                .contains(extension.toLowerCase());
    }
}
