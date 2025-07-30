package com.accenture.claims.ai.guardrails;

import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.PolicySelectionFlagStore;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import io.quarkiverse.langchain4j.guardrails.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocca qualsiasi tool finché non è stata selezionata una polizza
 * Impone di chiamare <code>setSelectedPolicy</code> dopo la scelta.
 */
@ApplicationScoped
public class FinalOutputGuard implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(FinalOutputGuard.class);

    @Inject GuardrailsContext ctx;
    @Inject
    PolicySelectionFlagStore flagStore;

    /* ────────────────────────────────────────────────────────── */

    @Override
    public OutputGuardrailResult validate(OutputGuardrailParams p) {
        String sessionId = ctx.getSessionId();
        LOG.info("FinalOutputGuard checking response for session: " + sessionId);
        LOG.info("Flag Pending Policy for session: " + flagStore.isPending(sessionId));
        if (flagStore.isPending(sessionId)) {

            AiMessage ai = p.responseFromLLM();
            // permetti SOLO testo che termini con “?”
            boolean justAQuestion =
                    ai != null &&
                            ai.text() != null &&
                            ai.text().trim().endsWith("?");

            if (justAQuestion) return success();

            return reprompt(
                    "⚠️ ERRORE: Devi chiamare setSelectedPolicy prima di procedere allo STEP 3 ⚠️",
                    """
                    ⚠️ ISTRUZIONE OBBLIGATORIA ⚠️
                    
                    Hai già mostrato le polizze all'utente, ma NON hai chiamato il tool setSelectedPolicy.
                    
                    DEVI ASSOLUTAMENTE:
                    1. Se l'utente non ha ancora scelto una polizza:
                       - Chiedi all'utente di scegliere una polizza specifica
                       - Attendi la risposta dell'utente
                    
                    2. DOPO che l'utente ha scelto o confermato una polizza:
                       - Chiama IMMEDIATAMENTE il tool setSelectedPolicy con:
                         * sessionId: lo stesso sessionId usato per retrievePolicy
                         * policyNumber: il numero della polizza scelta dall'utente
                    
                    Esempio:
                    setSelectedPolicy(sessionId, "MTRHHR00026398")
                    
                    NON puoi procedere allo STEP 3 senza prima chiamare questo tool.
                    """
            );
        }

        /* Nessuna scelta pendente → tutto OK */
        LOG.info("No pending policy selection for session: " + sessionId + " - allowing response");
        return success();
    }

}
