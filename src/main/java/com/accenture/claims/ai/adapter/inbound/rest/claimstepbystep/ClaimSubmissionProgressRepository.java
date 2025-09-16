package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;// Java

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClaimSubmissionProgressRepository implements PanacheMongoRepository<ClaimSubmissionProgress> {

    public ClaimSubmissionProgress findBySessionId(String sessionId) {
        return find("sessionId", sessionId).firstResult();
    }

    public void upsertBySessionId(String sessionId, ParsingResult res) {
        var existing = findBySessionId(sessionId);
        if (existing != null) {
            existing.setParsingResult(res);
            update(existing);
        } else {
            var doc = new ClaimSubmissionProgress();
            doc.setSessionId(sessionId);
            doc.setParsingResult(res);
            persist(doc);
        }
    }
}