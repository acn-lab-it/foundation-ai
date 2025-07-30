package com.accenture.claims.ai.guardrails;

import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.PolicySelectionFlagStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FinalOutputGuard implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(FinalOutputGuard.class);

    @Inject GuardrailsContext            ctx;
    @Inject PolicySelectionFlagStore     flagStore;
    @Inject SessionLanguageContext       sessionLanguageContext;
    @Inject LanguageHelper               languageHelper;

    /* ────────────────────────────────────────────────────────── */

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams p) {
        String sessionId = ctx.getSessionId();
        LOG.infof("FinalOutputGuard – session %s  (pending=%s)",
                sessionId, flagStore.isPending(sessionId));

        /* se non c’è una scelta pendente, lascia passare */
        if (!flagStore.isPending(sessionId)) return success();

        AiMessage ai = p.responseFromLLM();
        boolean justAQuestion =
                ai != null &&
                        ai.text() != null &&
                        ai.text().trim().endsWith("?");

        if (justAQuestion) return success();   // l’AI sta solo chiedendo quale polizza

        /* ───────── reprompt localizzato ───────── */

        String lang = sessionLanguageContext.getLanguage(sessionId); // fallback “en”
        LanguageHelper.PromptResult pr =
                languageHelper.getPromptWithLanguage(lang, "guardrails.finaloutputPrompt");

        String repromptMsg = pr.prompt;  // se servono variabili, usa applyVariables

        return reprompt(
                "setSelectedPolicy mandatory",
                repromptMsg
        );
    }
}
