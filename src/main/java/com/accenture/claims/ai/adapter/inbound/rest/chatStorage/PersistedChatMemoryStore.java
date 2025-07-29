package com.accenture.claims.ai.adapter.inbound.rest.chatStorage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.commons.lang.time.DateUtils.truncate;

/**
 * DOCUMENTATO QUI:
 * https://docs.quarkiverse.io/quarkus-langchain4j/dev/messages-and-memory.html
 */

//@TODO: Gestire i messaggi dei tool come tali (ora vanno in default > USER)
@ApplicationScoped
public class PersistedChatMemoryStore implements ChatMemoryStore {

    @Inject MongoClient mongo;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "local_db")
    String dbName;

    private MongoCollection<Document> coll() {
        return mongo.getDatabase(dbName).getCollection("chat_memory");
    }

    @PostConstruct
    void init() {
        coll().createIndex(
            new Document("createdAt", 1),
            new IndexOptions().expireAfter(1L, TimeUnit.DAYS)
        );
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Document doc = coll().find(Filters.eq("_id", memoryId.toString())).first();
        if (doc == null) return List.of();
        List<Document> stored = (List<Document>) doc.getOrDefault("messages", List.of());

        List<Document> filtered = stored.stream()
                .filter(d -> !"TOOL".equals(d.getString("type")))
                .toList();

        // Ne teniamo 6 in contesto (3 botta e risposta)
        int N = 6;
        int from = Math.max(0, filtered.size() - N);
        List<Document> window = filtered.subList(from, filtered.size());

        List<ChatMessage> out = new ArrayList<>();
        for (Document d : window) {
            String type = d.getString("type");
            String text = d.getString("text");
            if (text == null) continue;
            switch (type) {
                case "SYSTEM" -> out.add(SystemMessage.from(text));
                case "AI"     -> out.add(AiMessage.from(text));
                default       -> out.add(UserMessage.from(text));
            }
        }
        return out;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        //System.out.println(">>> updateMessages " + memoryId + " size=" + messages.size());
        List<Document> msgs = messages.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());

        Document out = new Document("_id", memoryId.toString())
                .append("createdAt", new Date())
                .append("messages", msgs);

        coll().replaceOne(Filters.eq("_id", memoryId.toString()), out, new ReplaceOptions().upsert(true));
        //System.out.println(">>> Persist OK id=" + memoryId + " messages=" + msgs.size());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        //System.out.println("DELETING");
        coll().deleteOne(Filters.eq("_id", memoryId.toString()));
    }

    private Document toDocument(ChatMessage msg) {
        if (msg instanceof SystemMessage m) {
            return new Document("type","SYSTEM").append("text", m.text());
        } else if (msg instanceof AiMessage m) {
            return new Document("type","AI").append("text", m.text());
        } else if (msg instanceof UserMessage m) {
            String text = m.contents().stream()
                    .filter(TextContent.class::isInstance)
                    .map(c -> ((TextContent) c).text())
                    .collect(Collectors.joining("\n"));
            return new Document("type","USER").append("text", text);
        } else if (msg instanceof  ToolExecutionResultMessage t){
            return new Document("type","TOOL")
                    .append("toolName", t.toolName());
                    //.append("text", t.text());
        }
        return new Document("type","USER").append("text", msg.toString());
    }
}

