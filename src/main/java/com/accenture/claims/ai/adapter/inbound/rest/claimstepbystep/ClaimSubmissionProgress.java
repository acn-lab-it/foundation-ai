package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;

@Data
@MongoEntity
public class ClaimSubmissionProgress extends PanacheMongoEntity {
    private String sessionId;
    private EmailParsingResult emailParsingResult;

}
