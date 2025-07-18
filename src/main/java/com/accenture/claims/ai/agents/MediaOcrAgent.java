package com.accenture.claims.ai.agents;

import com.accenture.claims.ai.dto.ImageSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MediaOcrAgent {

    private final ChatModel visionModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public MediaOcrAgent(ChatModel visionModel) {
        this.visionModel = visionModel;
    }

    /* API usata dal SuperAgent */
    public String runOcr(List<ImageSource> sources, String userText) throws IOException, InterruptedException {
        List<Image> images = new ArrayList<>();
        int budgetLeft = MAX_TOTAL_IMAGES;

        for (ImageSource s : sources) {
            Path path = Path.of(s.getRef());

            if (budgetLeft == 0) break;

            if (isVideo(path)) {                         // VIDEO
                List<Image> frames = extractFrames(path, budgetLeft);
                images.addAll(frames);
                budgetLeft -= frames.size();
            } else {                                     // IMMAGINE
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String b64 = Base64.getEncoder().encodeToString(bytes);
                    images.add(Image.builder()
                            .url("data:image/jpeg;base64," + b64)
                            .build());
                    budgetLeft--;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        String visionJson = analyze(images, userText);
        System.out.println(visionJson);
        return visionJson;
    }

    /* Analisi di foto + input utente */
    private String analyze(List<Image> images, String userText) {

        List<ChatMessage> prompt = buildPrompt(images, userText);

        ChatResponse resp = visionModel.chat(ChatRequest.builder()
                .messages(prompt)
                .temperature(0.0)
                .maxOutputTokens(1_024)
                .build());

        String raw  = resp.aiMessage().text();
        System.out.println("============= \n" + raw + "\n=============");

        return extractJsonBlock(raw).orElse(
                """
                {
                  "damageCategory":"NONE","damagedEntity":"NONE",
                  "eventType":"NONE","propertyCode":"NONE",
                  "claimDate":"NONE","claimHour":"NONE",
                  "claimProofDate":"NONE","claimReceivedDate":"NONE",
                  "ready":false,"confidence":0
                }""");
    }

    /* Il prompt "main" per l'analisi*/
    private List<ChatMessage> buildPrompt(List<Image> imgs, String userText) {

        List<ChatMessage> list = new ArrayList<>();

        String now = new Date().toString();
        list.add(SystemMessage.from(
                """
                Analizza tutte le immagini seguenti.
                
                Questi sono i tipi di eventi che devi identificare. Scegli il piu appropriato sulla base della tua analisi.
                - Incendio o altri eventi
                - Bagnatura e Spese di Ricerca e Riparazione del guasto per rottura di tubi di acqua e gas
                - Eventi Atmosferici
                - Fenomeno Elettrico
                - Eventi socio-politici, terrorismo, atti vandalici
                - Danni accidentali ai Vetri
                - Eccedenza consumo d’acqua
                - Catastrofe naturale
                - Spese Veterinarie
                - Furto e guasti causati da ladri
                
                 Qui hai una lista di codici, sulla base della macrocategoria che individui devi tornare il codice:
                | Property                       | Code  |
                | ------------------------------ | ------|
                | Building                       | RNMBS |
                | Contents                       | RNMCS |
                | Theft and Robbery              | RNMRS |
                | Home Civil Liability           | RNMOS |
                | Legal Protection               | RNMLS |
                
                Usa le informazioni passate dall'utente anche per definire:
                - claimDate: giorno in cui è avvenuto il sinistro (e.g. '2025-07-17'),
                - claimHour: ora in cui è avvenuto il sinistro (e.g. '08:00'),
                - claimProofDate: ora in cui è stata data prova del sinistro (e.g. '2025-07-18'),
                - claimReceivedDate: ora in cui è stato ricevuto dall'assicurazione il sinistro (e.g. '2025-07-18'),
                Sapendo che oggi è """+now+"""
                
                Se queste informazioni non sono fornite, non desumerle da solo ma chiedile. Sappi che claimDate e claimHour,
                nel caso non siano espresse esattamente come tali potrebbero essere contestualizzate in frasi tipo "ieri alle 20" o "due giorni fa".
                Potrebbe non essere detta in modo diretta ma deducibile.
                
                claimProofDate e claimReceivedData non sono dati mandatori, per cui se non li hai ma hai **TUTTO** il resto, puoi dedurre
                di avere tutte le informazioni che ti servono.
                
                Se hai tutte le informazioni, imposta il campo "ready" a true, altrimenti false
                
                Rispondi con **solo** il JSON:
                {
                  "damageCategory": "VEHICLE | PROPERTY | NONE",
                  "damagedEntity":  "<breve nome o NONE>",
                  "eventType": "<type of detected damage source (e.g. Incendio o altri eventi, Eventi Atmosferici)>",
                  "propertyCode": "<RNMBS | RNMCS | RNMRS | RNMOS | RNMLS>"
                  "claimDate": "<date o NONE>",
                  "claimHour": "<time o NONE>",
                  "claimProofDate": "<date o NONE>",
                  "claimReceivedDate": "<date o NONE>",
                  "ready": true | false,
                  "confidence": "<decimale 0‑1>"
                }
                Nient’altro.
                """));

        if (userText != null && !userText.isBlank()) {
            list.add(UserMessage.from(userText));
        }

        for (Image img : imgs) {
            ImageContent content = ImageContent.from(img.url());
            list.add(new UserMessage(content));
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
