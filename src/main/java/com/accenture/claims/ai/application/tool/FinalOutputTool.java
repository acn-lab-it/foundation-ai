package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class FinalOutputTool {

    @Inject FinalOutputStore store;
    private static final ObjectMapper M = new ObjectMapper();

    /** Lettura FINAL_OUTPUT */
    @Tool(
            name = "getFinalOutput",
            value = "Restituisce il FINAL_OUTPUT corrente (solo lettura)"
    )
    public String getFinalOutput(String sessionId) {
        ObjectNode fo = store.get(sessionId);
        System.out.println("========== FINAL_OUTPUT RETRIEVED ==========");
        System.out.println(fo);
        System.out.println("===========================================\n");
        return fo.toString();
    }

    @Tool(
            name  = "updateFinalOutput",
            value = """
        Fa il merge dei campi in FINAL_OUTPUT.
        Parametri:
          • sessionId  – string
          • patch      – oggetto JSON con SOLO i campi da aggiornare
        """
    )
    public String updateFinalOutput(String sessionId,
                                    Map<String, Object> patch) {
        ObjectNode p = M.valueToTree(patch);
        store.merge(sessionId, p);

        System.out.println("========== FINAL_OUTPUT UPDATED ==========");
        System.out.println(p);
        System.out.println("==========================================");

        return "{\"status\":\"OK\"}";
    }
}
