package com.accenture.claims.ai.adapter.outbound.persistence.model.emailParsing;

import com.accenture.claims.ai.adapter.outbound.persistence.model.damage.AdministrativeCheckEntity;
import com.accenture.claims.ai.adapter.outbound.persistence.model.damage.CircumstancesEntity;
import com.accenture.claims.ai.adapter.outbound.persistence.model.damage.MediaEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonExtraElements;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@MongoEntity(collection = "email_parsing_result")
@BsonDiscriminator
public class EmailParsingResultEntity extends PanacheMongoEntity {

    private String emailId;
    private String sessionId;
    private String incidentDate;
    private String incidentLocation;
    private String policyNumber;

    private ReporterEntity reporter;

    private AdministrativeCheckEntity administrativeCheck;
    private CircumstancesEntity circumstances;
    private String damageDetails;
    private String policyStatus;
    private List<MediaEntity> uploadedMedia;
    private String whatHappenedCode;
    private String whatHappenedContext;
    private String formattedAddress;

    // store any other unmapped fields
    @BsonExtraElements
    private Map<String, Object> otherFields;
}
