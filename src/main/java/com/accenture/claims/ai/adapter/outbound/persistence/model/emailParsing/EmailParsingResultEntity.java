package com.accenture.claims.ai.adapter.outbound.persistence.model.emailParsing;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonExtraElements;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@MongoEntity(collection = "email_parsing_result")
@BsonDiscriminator
public class EmailParsingResultEntity extends PanacheMongoEntity {

    private String emailId;
    private String incidentDate;
    private String incidentLocation;
    private String policyNumber;

    private ReporterEntity reporter;

    // store any other unmapped fields
    @BsonExtraElements
    private Map<String, Object> otherFields;
}
