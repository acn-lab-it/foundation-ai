package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.adapter.inbound.rest.dto.ImageSource;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class MediaOcrAgent {

    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject
    LanguageHelper languageHelper;

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

    public MediaOcrAgent(ChatModel visionModel) {
        this.visionModel = visionModel;
    }

    /**
     * Tool LLM: effettua OCR / classificazione danni su immagini o video.
     * @param sessionId id della sessione corrente
     * @param filePaths elenco di path assoluti sul filesystem server.
     * @param userText testo inputato dall'utente
     * @return JSON con damageCategory / damagedEntity / confidence
     */
    @Tool("""
    Analyze uploaded media (images or videos).
    Parameters:
    - sessionId: string
    - filePaths: array of LOCAL FILE PATHS (Windows/Unix).
    - userText: free text from the user.
    
    Behavior:
    - This tool LOADS the files and converts them to base64 data URLs internally.
    - Never echo file system paths in the answer.
    - Return ONLY a JSON with damageCategory, damagedEntity, confidence, dates, etc.
    """)
    public String analyzeMedia(String sessionId, List<String> filePaths, String userText) throws IOException, InterruptedException {
        List<ImageSource> src = filePaths.stream()
                .map(ImageSource::new)
                .collect(Collectors.toList());
        return runOcr(sessionId, src, userText == null ? "" : userText);
    }

    public String runOcr(String sessionId, List<ImageSource> sources, String userText) throws IOException, InterruptedException {
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
        return analyze(sessionId, images, userText);
    }

    /* Analisi di foto + input utente */
    private String analyze(String sessionId, List<Image> images, String userText) {
        List<ChatMessage> prompt = buildPrompt(sessionId, images, userText);
        ChatResponse resp = visionModel.chat(ChatRequest.builder()
                .messages(prompt)
                .temperature(0.0)
                .maxOutputTokens(1_024)
                .build());
        String raw = resp.aiMessage().text();
        return extractJsonBlock(raw).orElse(DEFAULT_JSON_FALLBACK);
    }


    /* Il prompt "main" per l'analisi*/
    private List<ChatMessage> buildPrompt(String sessionId, List<Image> imgs, String userText) {
        List<ChatMessage> list = new ArrayList<>();
        String lang = sessionLanguageContext.getLanguage(sessionId);

        // Recupera il prompt (passare 'lang' va bene: viene trattato come Accept-Language)
        LanguageHelper.PromptResult promptResult = languageHelper.getPromptWithLanguage(lang, "mediaOcr.mainPrompt");
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

    /*
     * ------------------------------------------------------
     * Sezione Gestione Video
     * REMINDER PRO-FUTURO: qua serve ffmpeg installato nel pc/container/ovunque giri il servizio
     * https://ffmpeg.org/download.html
     * ------------------------------------------------------
     */
    private static final int MAX_TOTAL_IMAGES     = 50;   // limite API Vision
    private static final int FRAME_INTERVAL_SEC   = 30;   // 1 frame ogni 30″
    private static final List<String> VIDEO_EXT   =  List.of("mp4","mov","avi","mkv","webm");

    private boolean isVideo(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = (dot == -1) ? "" : name.substring(dot + 1);
        return VIDEO_EXT.contains(ext);
    }

    private List<Image> extractFrames(Path video,
                                      int maxFrames) throws IOException, InterruptedException {

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
            // pulizia best‑effort
            try (var s = Files.list(tmpDir)) {
                s.forEach(fp -> fp.toFile().delete());
            }
            tmpDir.toFile().delete();
        }
        }
}
