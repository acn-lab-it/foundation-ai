package com.accenture.claims.ai.guardrails;

import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@ApplicationScoped
public class NoProgressWithoutToolGuard implements OutputGuardrail {

    @Inject
    GuardrailsContext guardCtx;
    @Inject SessionLanguageContext   sessionLangCtx;
    @Inject LanguageHelper           languageHelper;

    /** cache lang ➜ Pattern compilato */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams params) {

        // 1. recupero la lingua della sessione
        String sessionId = guardCtx.getSessionId();               // già impostato in FnolResource
        String lang      = sessionLangCtx.getLanguage(sessionId); // fallback interno a "en"

        // 2. pattern per quella lingua (lazy‑load + cache)
        Pattern progressPattern = patternCache.computeIfAbsent(lang.toLowerCase(Locale.ROOT), this::loadPattern);

        var aiMsg = params.responseFromLLM();

        /* se la risposta invoca tool → ok */
        if (aiMsg != null && aiMsg.toolExecutionRequests() != null && !aiMsg.toolExecutionRequests().isEmpty()) {
            return success();
        }

        String text = aiMsg == null ? "" : Optional.ofNullable(aiMsg.text()).orElse("").trim();
        if (text.isEmpty()) {
            return success();       // ci penserà l’altro guard‑rail a gestire il vuoto
        }

        /* match “messaggio di attesa” */
        if (progressPattern.matcher(text).find()) {

            String reprompt = languageHelper
                    .getPrompt(lang, "guardrails.progressReprompt")   // Optional<String>
                    .orElse("""
                       REGOLA FONDAMENTALE:
                       - Non annunciare che "stai per verificare" o "procedere".
                       - Se serve un tool, chiamalo ORA e restituisci il risultato adesso.
                       - Se mancano dati obbligatori, fai una domanda mirata e termina la risposta.
                     """);

            return reprompt("Niente messaggi di attesa. Usa i tool o poni una domanda mirata.", reprompt);
        }

        return success();
    }

    /* ------------------------------------------------------------------ */
    /* -----------------------  helper privati  ------------------------- */
    /* ------------------------------------------------------------------ */

    /**
     * Costruisce il Pattern leggendo da MongoDB la lista <code>guardrails.progressWords</code>
     * (array di stringhe). Se non trovata, usa un set di default ENG/ITA.
     */
    private Pattern loadPattern(String lang) {
        List<String> words = languageHelper
                .getPrompt(lang, "guardrails.progressWords")  // Optional<String> con JSON/CSV
                .map(this::parseWordsList)
                .orElseGet(this::defaultWords);

        // escape e join con “|”
        String regex = words.stream()
                .filter(s -> !s.isBlank())
                .map(Pattern::quote)
                .sorted((a, b) -> Integer.compare(b.length(), a.length())) // più lunghi prima
                .reduce((a, b) -> a + "|" + b)
                .orElse("");

        return Pattern.compile("\\b(" + regex + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Converte la stringa letta dal DB in lista parole (accetta JSON array o CSV). */
    private List<String> parseWordsList(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // è un JSON array semplice ["aaa","bbb"]
            return Arrays.stream(trimmed.substring(1, trimmed.length() - 1)
                            .split(","))
                    .map(s -> s.replaceAll("[\"\\[\\]]", "").trim())
                    .toList();
        }
        // fallback: CSV
        return Arrays.stream(trimmed.split(",")).map(String::trim).toList();
    }

    /** Parole di ripiego se in DB non è presente l’array. */
    private List<String> defaultWords() {
        return List.of(
                // ITA
                "un momento", "procedo", "verifico", "sto", "in corso", "controllo", "un attimo", "attendere",
                // ENG
                "please wait", "waiting", "checking", "processing"
        );
    }
}
