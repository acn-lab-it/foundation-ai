package com.accenture.claims.ai.guardrails;

import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Guardrail che garantisce il popolamento progressivo di FINAL_OUTPUT
 * e impedisce di saltare gli step obbligatori.
 */
@ApplicationScoped
public class FinalOutputGuard implements OutputGuardrail {

    @Inject GuardrailsContext ctx;
    @Inject FinalOutputStore  store;

    /* ====================================================================== */
    /*  MAIN                                                                  */
    /* ====================================================================== */

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams params) {

        /* JSON corrente (può essere null al primissimo turno) */
        ObjectNode fo = store.get(ctx.getSessionId());

        /* ------------------------------------------------------------------ */
        /* 1. Se non abbiamo ancora FIRST / LAST NAME blocchiamo tutto        */
        /* ------------------------------------------------------------------ */
        boolean hasReporter =
                hasNonEmpty(fo,"reporter","firstName") &&
                        hasNonEmpty(fo,"reporter","lastName");

        AiMessage ai = params.responseFromLLM();
        List<ToolExecutionRequest> requests =
                ai == null ? List.of() : ai.toolExecutionRequests();

        if (!hasReporter) {
            // consentiamo SOLO getFinalOutput / updateFinalOutput
            boolean onlyFoTools = requests.stream()
                    .allMatch(r -> "getFinalOutput".equals(r.name())
                            || "updateFinalOutput".equals(r.name()));

            if (!onlyFoTools) {
                return reprompt(
                        "Devi prima salvare nome e cognome con updateFinalOutput.",
                        "Chiedi firstName / lastName, poi chiama:\n" +
                                "updateFinalOutput({ \"reporter\":{ \"firstName\":\"…\",\"lastName\":\"…\" } })"
                );
            }
            // sta solo chiedendo i dati o aggiornando il reporter → ok
            return success();
        }

        /* ------------------------------------------------------------------ */
        /* 2. Verifica campi obbligatori in base allo STEP corrente           */
        /* ------------------------------------------------------------------ */
        int currentStep   = detectStep(fo);
        boolean stepReady = switch (currentStep) {
            case 1 -> hasReporter;
            case 2 -> hasNonEmpty(fo,"policyNumber")
                    && hasNonEmpty(fo,"policyStatus");
            case 3 -> hasNonEmpty(fo,"incidentDate")
                    && hasNonEmpty(fo,"incidentLocation")
                    && hasNonEmpty(fo,"whatHappenedContext")
                    && hasNonEmpty(fo,"whatHappenedCode");
            case 4 -> hasNonEmpty(fo,"damageDetails")
                    && hasNonEmpty(fo,"circumstances","details");
            case 5 -> fo.path("administrativeCheck").hasNonNull("passed");
            case 6 -> allFieldsPresent(fo);
            default -> true;                 // step 0 (welcome)
        };

        /* ------------------------------------------------------------------ */
        /* 3. Tolleranza: se lo step non è pronto MA                           */
        /*    - l’AI NON invoca tool di step successivi                       */
        /*    - e sta soltanto ponendo una domanda all’utente                 */
        /*    → lasciamo passare la risposta                                  */
        /* ------------------------------------------------------------------ */
        /*if (!stepReady) {
            boolean onlyQuestion =
                    requests.isEmpty()              // nessun tool
                            && ai != null
                            && ai.text() != null
                            && !ai.text().isBlank();           // c'è testo

            if (onlyQuestion) return success();

            // altrimenti blocchiamo e repromptiamo
            return reprompt(
                    "Mancano dati obbligatori per lo STEP " + currentStep + ".",
                    "Recupera le informazioni mancanti, chiama " +
                            "updateFinalOutput con i nuovi valori e poi continua."
            );
        }
*/
        /* Tutto in regola */
        return success();
    }

    /* ====================================================================== */
    /*  HELPER                                                                */
    /* ====================================================================== */

    /** Deduce lo step corrente (0–6) in base ai campi valorizzati. */
    private int detectStep(ObjectNode fo) {
        if (fo == null || fo.isEmpty())                                          return 0; // welcome

        if (!hasNonEmpty(fo,"reporter","firstName")
                || !hasNonEmpty(fo,"reporter","lastName"))                              return 1;

        if (!hasNonEmpty(fo,"policyNumber")
                || !hasNonEmpty(fo,"policyStatus"))                                     return 2;

        if (!hasNonEmpty(fo,"incidentDate")
                || !hasNonEmpty(fo,"incidentLocation")
                || !hasNonEmpty(fo,"whatHappenedContext")
                || !hasNonEmpty(fo,"whatHappenedCode"))                                 return 3;

        if (!hasNonEmpty(fo,"damageDetails")
                || !hasNonEmpty(fo,"circumstances","details"))                          return 4;

        if (!fo.path("administrativeCheck").hasNonNull("passed"))                return 5;

        return 6; // tutti i campi presenti
    }

    /** Verifica presenza di TUTTI i campi richiesti per lo STEP 6. */
    private boolean allFieldsPresent(ObjectNode fo) {
        return  hasNonEmpty(fo,"incidentDate")
                && hasNonEmpty(fo,"policyNumber")
                && hasNonEmpty(fo,"policyStatus")
                && fo.path("administrativeCheck").hasNonNull("passed")
                && hasNonEmpty(fo,"whatHappenedContext")
                && hasNonEmpty(fo,"whatHappenedCode")

                && hasNonEmpty(fo,"reporter","firstName")
                && hasNonEmpty(fo,"reporter","lastName")
                && hasNonEmpty(fo,"reporter","contacts","email")
                && hasNonEmpty(fo,"reporter","contacts","mobile")

                && hasNonEmpty(fo,"incidentLocation")

                && hasNonEmpty(fo,"circumstances","details")
                && fo.path("circumstances").has("notes")        // può essere null ma deve esistere

                && hasNonEmpty(fo,"damageDetails")
                && fo.has("imagesUploaded");                    // array – può essere vuoto
    }

    /** True se root.path(a).path(b)… è presente e non‑null. */
    private boolean hasNonEmpty(ObjectNode root, String... path) {
        if (root == null) return false;
        ObjectNode node = root;
        for (int i = 0; i < path.length - 1; i++) {
            if (!node.has(path[i]) || !node.path(path[i]).isObject()) return false;
            node = (ObjectNode) node.path(path[i]);
        }
        return node.hasNonNull(path[path.length - 1]);
    }
}
