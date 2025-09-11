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

@ApplicationScoped
public class FinalOutputJSONStore {

    @Inject MongoClient mongo;
    @Inject ObjectMapper mapper;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "local_db")
    String dbName;

    public ObjectNode get(@NonNull String collection, @NonNull String sessionId) {
        MongoCollection<Document> coll = mongo.getDatabase(dbName).getCollection(collection);
        Document doc = coll.find(Filters.eq("_id", sessionId)).first();
        if (doc == null) {
            return mapper.createObjectNode();
        }
        doc.remove("_id");
        return (ObjectNode) mapper.convertValue(doc, JsonNode.class);
    }

    public void put(@NonNull String collection,
                    @NonNull String sessionId,
                    String path,
                    @NonNull ObjectNode patch) {

        ObjectNode target = get(collection, sessionId);

        if (path == null || path.isBlank()) {
            deepMerge(target, patch);
        } else {
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

    public ObjectNode get(@NonNull String collection,
                          @NonNull String sessionId,
                          @NonNull String emailId) {
        MongoCollection<Document> coll = mongo.getDatabase(dbName).getCollection(collection);
        Document doc = coll.find(Filters.and(
                Filters.eq("_id", sessionId),
                Filters.eq("emailId", emailId)
        )).first();
        if (doc == null) {
            return mapper.createObjectNode();
        }
        doc.remove("_id");
        doc.remove("emailId");
        return (ObjectNode) mapper.convertValue(doc, JsonNode.class);
    }

    public void put(@NonNull String collection,
                    @NonNull String sessionId,
                    @NonNull String emailId,
                    String path,
                    @NonNull ObjectNode patch) {

        ObjectNode target = get(collection, sessionId, emailId);

        if (path == null || path.isBlank()) {
            deepMerge(target, patch);
        } else {
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
                    deepMerge((ObjectNode) node.get(p), patch);
                }
            }
        }

        System.out.println("======================= SAVING (with emailId) ==========================");
        System.out.println(target == null ? "<empty>" : target.toPrettyString());
        System.out.println("=======================================================================");

        Document toSave = Document.parse(target.toString())
                .append("_id", sessionId)
                .append("emailId", emailId);

        MongoCollection<Document> coll = mongo.getDatabase(dbName).getCollection(collection);
        coll.replaceOne(
                Filters.and(Filters.eq("_id", sessionId), Filters.eq("emailId", emailId)),
                toSave,
                new ReplaceOptions().upsert(true)
        );
    }

    /** Deep-merge ricorsivo (patch ↦ target) */
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
