package com.accenture.claims.ai.application.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SummaryTool {

    private final ObjectMapper mapper = new ObjectMapper();

    // Se il mapping con record ti ha sempre funzionato, puoi lasciarlo così.
    // In caso di problemi di deserializzazione, trasformalo in POJO con costruttore vuoto + getter/setter.
    public record MediaItem(String mediaName, String mediaDescription, String mediaType) {}

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    @Tool("""
        Genera il JSON finale del processo di FNOL. Usa SOLO questo tool allo STEP 5.
        La risposta al cliente deve essere ESCLUSIVAMENTE il JSON restituito da questo tool, senza testo aggiuntivo.
        Parametri attesi:
          - incidentDate (string, ISO-8601)
          - policyNumber (string)
          - policyStatus (string)
          - administrativeCheckPassed (boolean)
          - whatHappenedContext (string)
          - whatHappenedCode (string)
          - reporterFirstName (string)
          - reporterLastName (string)
          - reporterEmail (string, opzionale)
          - reporterMobile (string, opzionale)
          - incidentLocation (string)
          - circumstancesDetails (string)
          - circumstancesNotes (string)
          - damageDetails (string)
          - imagesUploaded (array di oggetti { mediaName, mediaDescription, mediaType })
        Output: un singolo JSON con la struttura finale.
        """)
    public String emitSummary(
            String incidentDate,
            String policyNumber,
            String policyStatus,
            boolean administrativeCheckPassed,
            String whatHappenedContext,
            String whatHappenedCode,
            String reporterFirstName,
            String reporterLastName,
            String reporterEmail,   // NEW (opzionale)
            String reporterMobile,  // NEW (opzionale)
            String incidentLocation,
            String circumstancesDetails,
            String circumstancesNotes,
            String damageDetails,
            List<MediaItem> imagesUploaded
    ) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("incidentDate", incidentDate);
            root.put("policyNumber", policyNumber);
            root.put("policyStatus", policyStatus);
            root.put("administrativeCheck", Map.of("passed", administrativeCheckPassed));
            root.put("whatHappenedContext", whatHappenedContext);
            root.put("whatHappenedCode", whatHappenedCode);

            // reporter + contacts (solo se almeno un contatto è presente)
            Map<String, Object> reporter = new LinkedHashMap<>();
            reporter.put("firstName", reporterFirstName);
            reporter.put("lastName", reporterLastName);
            Map<String, Object> contacts = new LinkedHashMap<>();
            if (notBlank(reporterEmail))  contacts.put("email", reporterEmail);
            if (notBlank(reporterMobile)) contacts.put("mobile", reporterMobile);
            if (!contacts.isEmpty()) {
                reporter.put("contacts", contacts);
            }
            root.put("reporter", reporter);

            root.put("incidentLocation", incidentLocation);
            root.put("circumstances", Map.of(
                    "details", circumstancesDetails,
                    "notes", circumstancesNotes
            ));
            root.put("damageDetails", damageDetails);
            root.put("imagesUploaded", imagesUploaded != null ? imagesUploaded : List.of());

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
