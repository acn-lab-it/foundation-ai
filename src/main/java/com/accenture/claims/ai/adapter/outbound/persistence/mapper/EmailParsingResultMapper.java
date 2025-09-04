package com.accenture.claims.ai.adapter.outbound.persistence.mapper;

import com.accenture.claims.ai.adapter.outbound.persistence.model.emailParsing.EmailParsingResultEntity;
import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "cdi", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EmailParsingResultMapper {

    EmailParsingResultEntity toEmailParsingResultEntity(EmailParsingResult emailParsingResult);

    EmailParsingResult toEmailParsingResult(EmailParsingResultEntity emailParsingResultEntity);

    default String map(ObjectId id) {
        return id == null ? null : id.toHexString();
    }

    default ObjectId map(String id) {
        return id == null ? null : new ObjectId(id);
    }
}
