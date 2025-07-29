package com.accenture.claims.ai.guardrails;

import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.regex.Pattern;

@ApplicationScoped
public class PromptInjectionGuard implements InputGuardrail {

    @Inject PromptInjectionDetectionService detector;
    @Inject
    GuardrailsContext ctx;

    @ConfigProperty(name = "guardrails.prompt-injection.threshold", defaultValue = "0.7")
    double threshold;

    private static final Pattern TOOL_ECHO =
            Pattern.compile("^ToolExecution(?:Result|Request)Message\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage == null ? "" : userMessage.singleText();
        if (text == null) text = "";

        if ("start".equalsIgnoreCase(text.trim())) {
            return success();
        }

        if (userMessage != null && TOOL_ECHO.matcher(userMessage.toString().trim()).find()) {
            return success(); // non è un input utente reale → bypass detection, è il tool
        }


        String systemPrompt = ctx.getSystemPrompt(); // valorizzato in FnolResource
        double score = detector.isInjection(systemPrompt == null ? "" : systemPrompt, text);

        if (score > threshold) {
            return fatal("Prompt injection detected (score=" + score + ")");
        }
        return success();
    }
}
