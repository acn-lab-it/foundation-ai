package com.accenture.claims.ai.guardrails;

import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
//@TODO - Verificare se forse dovrei passare un system message, così arriva come usermessage
public class NonEmptyOutputGuard implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams params) {
        var ai = params.responseFromLLM();
        // Se il modello ha richiesto l'esecuzione di tool, tutto ok
        /*if (ai != null && ai.toolExecutionRequests() != null && !ai.toolExecutionRequests().isEmpty()) {
            return success();
        }*/

        String text = (ai == null || ai.text() == null) ? "" : ai.text().trim();

        // intercetta output vuoti o "null"
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            String nudge = """
                REGOLA FONDAMENTALE:
                - La tua risposta precedente era vuota o nulla.
                - Ora DEVI fornire immediatamente una risposta non vuota e coerente con il System Prompt.
                - Se lo step richiede l'uso di tool, ESEGUI i tool necessari ORA e restituisci
                  il risultato nella stessa risposta (niente messaggi di attesa).
                """;
            return reprompt("Niente messaggi vuoti o nulli. Usa i tool o poni una domanda mirata.", nudge);
        }

        return success();
    }
}
