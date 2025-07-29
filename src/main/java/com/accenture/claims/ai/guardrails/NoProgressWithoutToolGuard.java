package com.accenture.claims.ai.guardrails;

import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Pattern;

@ApplicationScoped
public class NoProgressWithoutToolGuard implements OutputGuardrail {

    // italian + english “progress” words
    // @TODO - DEVO FARLO MULTILINGUA DINAMICO
    // @TODO - Gestire il fatto che ogni tanto torna null
    private static final Pattern PROGRESS = Pattern.compile(
            "\\b(un momento|verifico|verificando|procedo|controllando|un attimo|attendere|sto|in corso|verificher[òo]|controllo|a moment|please wait|waiting|checking|processing)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams params) {
        AiMessage ai = params.responseFromLLM();

        // Se il modello ha richiesto l'esecuzione di tool, tutto ok
        if (ai != null && ai.toolExecutionRequests() != null && !ai.toolExecutionRequests().isEmpty()) {
            return success();
        }

        String text = ai == null ? null : ai.text();
        if (text == null || text.isBlank()) {
            return success();
        }

        // Rileva messaggi “di attesa / progresso” senza tool
        if (PROGRESS.matcher(text).find()) {
            String reprompt = """
          REGOLA FONDAMENTALE:
          - Non annunciare che "stai per verificare" o "procedere".
          - Se per completare lo step serve un tool, chiamalo ORA e restituisci il risultato nella stessa risposta.
          - Fai riferimento ai tool che ti sono stati dati.
          - Se mancano dati obbligatori, fai una domanda specifica e termina la risposta.
          """;
            // Aggiunge il reprompt in memoria e rilancia automaticamente la chiamata
            return reprompt("Niente messaggi di attesa. Usa i tool o poni una domanda mirata.", reprompt);
        }

        return success();
    }
}
