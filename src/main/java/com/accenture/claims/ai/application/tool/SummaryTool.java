package com.accenture.claims.ai.application.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class SummaryTool {

    private final ObjectMapper mapper = new ObjectMapper();

    @Tool("Genera il JSON finale del processo di FNOL. Usa SOLO questo tool allo STEP 5. " +
            "La risposta al cliente deve essere ESCLUSIVAMENTE il JSON restituito da questo tool, senza testo aggiuntivo.")
    public String emitSummary(
            String incidentDate,
            String policyNumber,
            String policyStatus,
            boolean administrativeCheckPassed,
            String whatHappenedContext,
            String whatHappenedCode,
            String reporterFirstName,
            String reporterLastName,
            String incidentLocation,
            String circumstancesDetails,
            String circumstancesNotes,
            String damageDetails,
            String imagesUploaded
    ) {
        try {
            Map<String,Object> root = new LinkedHashMap<>();
            root.put("incidentDate", incidentDate);
            root.put("policyNumber", policyNumber);
            root.put("policyStatus", policyStatus);
            root.put("administrativeCheck", Map.of("passed", administrativeCheckPassed));
            root.put("whatHappenedContext", whatHappenedContext);
            root.put("whatHappenedCode", whatHappenedCode);
            root.put("reporter", Map.of("firstName", reporterFirstName, "lastName", reporterLastName));
            root.put("incidentLocation", incidentLocation);
            root.put("circumstances", Map.of("details", circumstancesDetails, "notes", circumstancesNotes));
            root.put("damageDetails", damageDetails);
            root.put("imagesUploaded", imagesUploaded);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
