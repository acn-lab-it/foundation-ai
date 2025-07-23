/*
package com.accenture.claims.ai.application.agent;

import com.accenture.claims.ai.adapter.outbound.rest.RestService;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;

@RegisterAiService
@SystemMessage("""
    Sei l’assistente sinistri Allianz.

    Se ricevi un blocco JSON **VisionAnalysis**:
      • Se contiene "ready": true
          - chiama il tool rest_api con:
            {
              "url": "http://localhost:3000/superagent/api/policy",
              "method": "GET"
            }
          - poi informa l'utente che la denuncia è stata inviata.
      • Altrimenti chiedi unicamente i dati mancanti per renderlo pronto.

    Se non c'è alcun JSON VisionAnalysis comportati come un normale
    assistente sinistri, ponendo le domande utili.

    Rispondi sempre in italiano.
    """)
public interface SuperAgent {

    */
/** @param userInput  testo dell’utente
     *  @param visionJson JSON generato dall’OCR (stringa vuota se assente)
     *//*

    @UserMessage("""
        {#if visionJson}
        VisionAnalysis:
        ```json
        {visionJson}
        ```
        {/if}
        Utente: {userInput}
        """)
    @ToolBox(RestService.class)
    String chat(String userInput, String visionJson);
}
*/
