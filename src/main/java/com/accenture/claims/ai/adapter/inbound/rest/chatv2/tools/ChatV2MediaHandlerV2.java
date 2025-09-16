package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class ChatV2MediaHandlerV2 {

    @Inject
    ChatV2MediaOcrAgentV2 mediaOcrAgent;

    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool("Process and categorize media files for damage analysis")
    public Map<String, Object> processMediaFilesV2(String sessionId, List<String> mediaFiles) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> processedMedia = new ArrayList<>();
        String damageCategory = "UNKNOWN";
        double maxConfidence = 0.0;

        try {
            if (mediaFiles == null || mediaFiles.isEmpty()) {
                result.put("imagesUploaded", new ArrayList<>());
                result.put("damageCategory", "NONE");
                result.put("confidence", 0.0);
                return result;
            }

            for (String filePath : mediaFiles) {
                try {
                    // Verifica che il file esista
                    if (!Files.exists(Path.of(filePath))) {
                        continue;
                    }

                    // Processa il file con MediaOcrAgent
                    String analysisResult = mediaOcrAgent.processMediaV2(sessionId, filePath);
                    
                    // Estrai informazioni dall'analisi
                    Map<String, Object> mediaInfo = extractMediaInfo(filePath, analysisResult);
                    processedMedia.add(mediaInfo);

                    // Aggiorna categoria danno se confidence è maggiore
                    String category = (String) mediaInfo.get("damageCategory");
                    double confidence = (Double) mediaInfo.getOrDefault("confidence", 0.0);
                    
                    if (confidence > maxConfidence && !"NONE".equals(category)) {
                        damageCategory = category;
                        maxConfidence = confidence;
                    }

                } catch (Exception e) {
                    // Log errore ma continua con altri file
                    Map<String, Object> errorInfo = Map.of(
                            "mediaName", Path.of(filePath).getFileName().toString(),
                            "mediaType", getFileType(filePath),
                            "error", "Error processing: " + e.getMessage(),
                            "confidence", 0.0
                    );
                    processedMedia.add(errorInfo);
                }
            }

            result.put("imagesUploaded", processedMedia);
            result.put("damageCategory", damageCategory);
            result.put("confidence", maxConfidence);
            result.put("processedCount", processedMedia.size());

        } catch (Exception e) {
            result.put("error", "Error processing media files: " + e.getMessage());
            result.put("imagesUploaded", new ArrayList<>());
            result.put("damageCategory", "UNKNOWN");
            result.put("confidence", 0.0);
        }

        return result;
    }

    @Tool("Build complete final JSON output")
    public ObjectNode buildFinalOutputV2(String sessionId) {
        try {
            ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
            if (finalOutput == null) {
                finalOutput = mapper.createObjectNode();
            }

            // Assicurati che tutti i campi richiesti siano presenti
            ensureRequiredFields(finalOutput);

            // Salva l'output aggiornato
            finalOutputJSONStore.put("final_output", sessionId, null, finalOutput);

            // Crea una copia per l'output esterno rimuovendo _internals
            ObjectNode publicOutput = finalOutput.deepCopy();
            publicOutput.remove("_internals");

            return publicOutput;

        } catch (Exception e) {
            ObjectNode errorOutput = mapper.createObjectNode();
            errorOutput.put("error", "Error building final output: " + e.getMessage());
            return errorOutput;
        }
    }

    @Tool("Update final output with media information")
    public void updateFinalOutputWithMediaV2(String sessionId, Map<String, Object> mediaData) {
        try {
            ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
            if (finalOutput == null) {
                finalOutput = mapper.createObjectNode();
            }

            // Aggiorna con informazioni media
            if (mediaData.containsKey("imagesUploaded")) {
                ArrayNode imagesArray = mapper.createArrayNode();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> images = (List<Map<String, Object>>) mediaData.get("imagesUploaded");
                
                for (Map<String, Object> image : images) {
                    ObjectNode imageNode = mapper.createObjectNode();
                    imageNode.put("mediaName", (String) image.getOrDefault("mediaName", "unknown"));
                    imageNode.put("mediaDescription", (String) image.getOrDefault("mediaDescription", ""));
                    imageNode.put("mediaType", (String) image.getOrDefault("mediaType", "unknown"));
                    imageNode.put("confidence", (Double) image.getOrDefault("confidence", 0.0));
                    imagesArray.add(imageNode);
                }
                
                finalOutput.set("imagesUploaded", imagesArray);
            }

            if (mediaData.containsKey("damageCategory")) {
                finalOutput.put("damageCategory", (String) mediaData.get("damageCategory"));
            }

            // Salva l'output aggiornato
            finalOutputJSONStore.put("final_output", sessionId, null, finalOutput);

        } catch (Exception e) {
            // Log errore ma non fallire
            System.err.println("Error updating final output with media: " + e.getMessage());
        }
    }

    private Map<String, Object> extractMediaInfo(String filePath, String analysisResult) {
        Map<String, Object> mediaInfo = new HashMap<>();
        
        try {
            // Parse del risultato dell'analisi
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = mapper.readValue(analysisResult, Map.class);
            
            String fileName = Path.of(filePath).getFileName().toString();
            String fileType = getFileType(filePath);
            
            mediaInfo.put("mediaName", fileName);
            mediaInfo.put("mediaType", fileType);
            mediaInfo.put("mediaDescription", analysis.getOrDefault("description", "Media analysis"));
            mediaInfo.put("damageCategory", analysis.getOrDefault("damageCategory", "UNKNOWN"));
            mediaInfo.put("damagedEntity", analysis.getOrDefault("damagedEntity", "UNKNOWN"));
            mediaInfo.put("eventType", analysis.getOrDefault("eventType", "UNKNOWN"));
            mediaInfo.put("confidence", analysis.getOrDefault("confidence", 0.0));
            mediaInfo.put("analysisResult", analysisResult);

        } catch (Exception e) {
            // Fallback se il parsing fallisce
            String fileName = Path.of(filePath).getFileName().toString();
            String fileType = getFileType(filePath);
            
            mediaInfo.put("mediaName", fileName);
            mediaInfo.put("mediaType", fileType);
            mediaInfo.put("mediaDescription", "Media analysis failed");
            mediaInfo.put("damageCategory", "UNKNOWN");
            mediaInfo.put("confidence", 0.0);
            mediaInfo.put("error", "Analysis parsing failed: " + e.getMessage());
        }

        return mediaInfo;
    }

    private String getFileType(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString();
        String extension = "";
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        }

        if (isImageFile(extension)) {
            return "image";
        } else if (isVideoFile(extension)) {
            return "video";
        } else {
            return "unknown";
        }
    }

    private boolean isImageFile(String extension) {
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "heic", "heif", "svg")
                .contains(extension);
    }

    private boolean isVideoFile(String extension) {
        return Set.of("mp4", "mov", "m4v", "avi", "mkv", "webm", "mpeg", "mpg", "3gp", "3gpp", "wmv")
                .contains(extension);
    }

    private void ensureRequiredFields(ObjectNode finalOutput) {
        // Campi obbligatori per Step 1
        if (!finalOutput.has("incidentDate")) {
            finalOutput.put("incidentDate", "");
        }
        if (!finalOutput.has("incidentLocation")) {
            finalOutput.put("incidentLocation", "");
        }
        if (!finalOutput.has("administrativeCheck")) {
            ObjectNode adminCheck = mapper.createObjectNode();
            adminCheck.put("passed", false);
            finalOutput.set("administrativeCheck", adminCheck);
        }

        // Campi obbligatori per Step 2
        if (!finalOutput.has("whatHappenedCode")) {
            finalOutput.put("whatHappenedCode", "");
        }
        if (!finalOutput.has("whatHappenedContext")) {
            finalOutput.put("whatHappenedContext", "");
        }
        if (!finalOutput.has("damageDetails")) {
            finalOutput.put("damageDetails", "");
        }
        if (!finalOutput.has("circumstances")) {
            ObjectNode circumstances = mapper.createObjectNode();
            circumstances.put("details", "");
            circumstances.put("notes", "");
            finalOutput.set("circumstances", circumstances);
        }

        // Campi opzionali
        if (!finalOutput.has("imagesUploaded")) {
            finalOutput.set("imagesUploaded", mapper.createArrayNode());
        }
        if (!finalOutput.has("damageCategory")) {
            finalOutput.put("damageCategory", "UNKNOWN");
        }
    }

    @Tool("Get media processing summary")
    public Map<String, Object> getMediaProcessingSummaryV2(String sessionId) {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
            if (finalOutput == null) {
                summary.put("hasMedia", false);
                summary.put("mediaCount", 0);
                return summary;
            }

            if (finalOutput.has("imagesUploaded") && finalOutput.get("imagesUploaded").isArray()) {
                ArrayNode imagesArray = (ArrayNode) finalOutput.get("imagesUploaded");
                summary.put("hasMedia", imagesArray.size() > 0);
                summary.put("mediaCount", imagesArray.size());
                
                // Calcola statistiche
                int imageCount = 0;
                int videoCount = 0;
                double avgConfidence = 0.0;
                
                for (int i = 0; i < imagesArray.size(); i++) {
                    ObjectNode image = (ObjectNode) imagesArray.get(i);
                    String mediaType = image.path("mediaType").asText();
                    double confidence = image.path("confidence").asDouble();
                    
                    if ("image".equals(mediaType)) {
                        imageCount++;
                    } else if ("video".equals(mediaType)) {
                        videoCount++;
                    }
                    avgConfidence += confidence;
                }
                
                if (imagesArray.size() > 0) {
                    avgConfidence /= imagesArray.size();
                }
                
                summary.put("imageCount", imageCount);
                summary.put("videoCount", videoCount);
                summary.put("averageConfidence", avgConfidence);
            } else {
                summary.put("hasMedia", false);
                summary.put("mediaCount", 0);
            }

        } catch (Exception e) {
            summary.put("error", "Error getting media summary: " + e.getMessage());
            summary.put("hasMedia", false);
            summary.put("mediaCount", 0);
        }

        return summary;
    }
}
