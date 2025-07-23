// RestTools.java ----------------------------------------------------------
package com.accenture.claims.ai.adapter.outbound.rest;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;

@ApplicationScoped
public class RestService {

    @Tool(name = "rest_api",
            value = """
          Chiama un endpoint HTTP.
          Input JSON:
          {
            "url":    "<endpoint completo>",
            "method": "GET | POST | PUT | DELETE",
            "body":   "<stringa JSON quando serve>"
          }
          Restituisci la risposta dell’API come stringa.
          """)
    public String restApi(String url, String method, String body) {
        try {
            // 1. Costruiamo il client
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();

            // 2. Request‑builder di base
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json");

            switch (method.toUpperCase()) {
                case "GET", "DELETE" ->                       // NO body
                        req.method(method, HttpRequest.BodyPublishers.noBody());

                case "POST", "PUT", "PATCH" -> {
                    req.header("Content-Type", "application/json");
                    req.method(method,
                            HttpRequest.BodyPublishers.ofString(
                                    body == null ? "" : body));
                }
                default -> throw new IllegalArgumentException("Metodo non supportato: " + method);
            }

            HttpResponse<String> resp =
                    client.send(req.build(), HttpResponse.BodyHandlers.ofString());

            return """
               { "status": %d, "body": %s }
               """.formatted(resp.statusCode(), escape(resp.body()));
        } catch (Exception e) {
            // fai arrivare comunque un JSON all’LLM, così possiamo fare una fallback? da capire con gestione errori
            return """
               { "status": 500, "error": "%s" }
               """.formatted(escape(e.getMessage()));
        }
    }
    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

}
