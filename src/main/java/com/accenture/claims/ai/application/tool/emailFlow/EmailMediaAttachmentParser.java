package com.accenture.claims.ai.application.tool.emailFlow;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ImageSource;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailMediaAttachmentParser {

    @Inject SessionLanguageContext sessionLanguageContext;
    @Inject LanguageHelper languageHelper;
    @Inject FinalOutputJSONStore finalOutputJSONStore;

    private final ChatModel visionModel;
    private final ObjectMapper mapper = new ObjectMapper();

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

    public EmailMediaAttachmentParser(ChatModel visionModel) {
        this.visionModel = visionModel;
    }

    @Tool(
            name = "email_media_attachment_parser",
            value = """
        Analyze uploaded media (images or videos).
        Parameters:
        - sessionId: string
        - emailId: string
        - filePaths: array of LOCAL FILE PATHS (Windows/Unix).
        - userText: free text from the user.
        Behavior:
        - This tool LOADS the files and converts them to base64 data URLs internally.
        - Never echo file system paths in the answer.
        - Return ONLY a JSON with damageCategory, damagedEntity, confidence, dates, etc.
        """
    )
    public String email_media_attachment_parser(String sessionId, String emailId, List<String> filePaths, String userText)
            throws IOException, InterruptedException {
        List<ImageSource> src = (filePaths == null)
                ? List.of()
                : filePaths.stream().map(ImageSource::new).collect(Collectors.toList());
        return runOcr(sessionId, emailId, src, userText == null ? "" : userText);
    }

    public String runOcr(String sessionId, String emailId, List<ImageSource> sources, String userText)
            throws IOException, InterruptedException {

        List<Image> images = new ArrayList<>();
        int budgetLeft = MAX_TOTAL_IMAGES;

        for (ImageSource s : sources) {
            Path path = Path.of(s.getRef());
            if (budgetLeft == 0) break;

            if (isVideo(path)) {
                List<Image> frames = extractFrames(path, budgetLeft);
                images.addAll(frames);
                budgetLeft -= frames.size();
            } else {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String b64 = Base64.getEncoder().encodeToString(bytes);
                    images.add(Image.builder().url("data:image/jpeg;base64," + b64).build());
                    budgetLeft--;
                } catch (IOException e) {
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

        try {
            JsonNode result = mapper.readTree(json);

            // ====== (A) Aggiorna FINAL_OUTPUT (logica invariata) ======
            ArrayNode imagesArr = mapper.createArrayNode();
            for (ImageSource s : sources) {
                ObjectNode m  = mapper.createObjectNode();
                m.put("mediaName", Path.of(s.getRef()).getFileName().toString());
                String descr = result.path("damageCategory").asText("UNKNOWN")
                        + " - "
                        + result.path("damagedEntity").asText("UNKNOWN");
                m.put("mediaDescription", descr);
                m.put("mediaType", isVideo(Path.of(s.getRef())) ? "video" : "image");
                imagesArr.add(m);
            }

            ObjectNode patch = mapper.createObjectNode();
            patch.set("imagesUploaded", imagesArr);

            ObjectNode circumstances = mapper.createObjectNode();
            circumstances.put("details", result.path("eventType").asText("UNKNOWN"));
            circumstances.put("notes", safe(userText));
            patch.set("circumstances", circumstances);

            patch.put("damageDetails", descrFromResult(result));

            finalOutputJSONStore.put("email_parsing_result", sessionId, emailId,null, patch);
            return mapper.writeValueAsString(patch);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return json;
    }

    private static String descrFromResult(JsonNode r) {
        return "%s - %s (conf. %.2f)".formatted(
                r.path("damageCategory").asText("UNKNOWN"),
                r.path("damagedEntity").asText("UNKNOWN"),
                r.path("confidence").asDouble(0.0)
        );
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private List<ChatMessage> buildPrompt(String sessionId, List<Image> imgs, String userText) {
        List<ChatMessage> list = new ArrayList<>();
        String lang = sessionLanguageContext.getLanguage(sessionId);

        LanguageHelper.PromptResult promptResult =
                languageHelper.getPromptWithLanguage(lang, "mediaOcr.mainPrompt");
        String finalSystem = languageHelper.applyVariables(
                promptResult.prompt,
                Map.of("today", new Date().toString())
        );
        list.add(SystemMessage.from(finalSystem));

        if (userText != null && !userText.isBlank()) {
            list.add(UserMessage.from(userText));
        }
        for (Image img : imgs) {
            list.add(new UserMessage(ImageContent.from(img.url())));
        }
        return list;
    }

    private Optional<String> extractJsonBlock(String s) {
        int start = s.indexOf('{');
        int end   = s.lastIndexOf('}');
        return (start >= 0 && end > start)
                ? Optional.of(s.substring(start, end + 1))
                : Optional.empty();
    }

    private static final int MAX_TOTAL_IMAGES   = 50;
    private static final int FRAME_INTERVAL_SEC = 30;
    private static final List<String> VIDEO_EXT = List.of("mp4","mov","avi","mkv","webm");

    private boolean isVideo(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = (dot == -1) ? "" : name.substring(dot + 1);
        return VIDEO_EXT.contains(ext);
    }

    private List<Image> extractFrames(Path video, int maxFrames) throws IOException, InterruptedException {
        Path tmpDir = Files.createTempDirectory("frames-");

        Process p = new ProcessBuilder(
                "ffmpeg",
                "-i", video.toString(),
                "-vf",
                "select='not(mod(n\\,"+FRAME_INTERVAL_SEC+"))',setpts=N/("+FRAME_INTERVAL_SEC+"*TB)",
                tmpDir.resolve("frame-%04d.jpg").toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();

        if (p.waitFor() != 0) {
            throw new IOException("ffmpeg exited with error");
        }

        try (var stream = Files.list(tmpDir)) {
            return stream
                    .filter(f -> f.toString().endsWith(".jpg"))
                    .sorted()
                    .limit(maxFrames)
                    .map(f -> {
                        try {
                            byte[] bytes = Files.readAllBytes(f);
                            String b64   = Base64.getEncoder().encodeToString(bytes);
                            return Image.builder()
                                    .url("data:image/jpeg;base64," + b64)
                                    .build();
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    })
                    .collect(Collectors.toList());
        } finally {
            try (var s = Files.list(tmpDir)) { s.forEach(fp -> fp.toFile().delete()); }
            tmpDir.toFile().delete();
        }
    }
}
