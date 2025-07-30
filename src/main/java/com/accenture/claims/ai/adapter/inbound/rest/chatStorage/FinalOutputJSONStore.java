package com.accenture.claims.ai.adapter.inbound.rest.chatStorage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;

@ApplicationScoped
public class FinalOutputJSONStore {

    @Inject MongoClient mongo;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "local_db")
    String dbName;

    /** Recupera (o crea vuoto) oggetto per quella sessione. */
    public ObjectNode get(@NonNull String collection, @NonNull String sessionId) {
        MongoCollection<Document> coll = mongo.getDatabase(dbName).getCollection(collection);
        Document doc = coll.find(Filters.eq("_id", sessionId)).first();
        if (doc == null) {
            return mapper.createObjectNode();        // non esiste ancora
        }
        doc.remove("_id");                           // pulizia
        return (ObjectNode) mapper.convertValue(doc, JsonNode.class);
    }

    /**
     * Fa un merge “profondo” in path (dot‑notation). Se il documento non esiste
     * lo crea; se il path è <code>null</code> merge a livello root.
     */
    public void put(@NonNull String collection,
                    @NonNull String sessionId,
                    String path,
                    @NonNull ObjectNode patch) {

        // 1. documento corrente (vuoto se non esiste)
        ObjectNode target = get(collection, sessionId);

        /* ───── root‑level patch ───────────────────────────── */
        if (path == null || path.isBlank()) {
            // Svuota l’oggetto e copia tutti i campi del patch alla radice
            deepMerge(target, patch);
        }
        /* ───── patch annidato ─────────────────────────────── */
        else {
            ObjectNode node = target;
            String[] parts = path.split("\\.");
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (!node.has(p) || !node.get(p).isObject()) {
                    node.set(p, mapper.createObjectNode());
                }
                if (i < parts.length - 1) {
                    node = (ObjectNode) node.get(p);
                } else {
                    // ultimo segmento → merge sul nodo finale
                    deepMerge((ObjectNode) node.get(p), patch);
                }
            }
        }

        System.out.println("======================= SAVING ==========================");
        System.out.println(target == null ? "<empty>" : target.toPrettyString());
        System.out.println("=================================================");

        Document toSave = Document.parse(target.toString()).append("_id", sessionId);
        MongoCollection<Document> coll = mongo.getDatabase(dbName).getCollection(collection);
        coll.replaceOne(Filters.eq("_id", sessionId), toSave, new ReplaceOptions().upsert(true));
    }

    /* ───── helpers ─────────────────────────────────────────────── */


    /** Deep‑merge ricorsivo (patch ↦ target) */
    private void deepMerge(ObjectNode target, ObjectNode patch) {
        patch.fields().forEachRemaining(e -> {
            String key = e.getKey();
            JsonNode value = e.getValue();
            if (value.isObject() && target.has(key) && target.get(key).isObject()) {
                deepMerge((ObjectNode) target.get(key), (ObjectNode) value);
            } else {
                target.set(key, value);
            }
        });
    }


}
