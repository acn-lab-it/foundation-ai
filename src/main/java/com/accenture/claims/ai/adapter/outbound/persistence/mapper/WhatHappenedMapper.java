package com.accenture.claims.ai.adapter.outbound.persistence.mapper;

import com.accenture.claims.ai.adapter.outbound.persistence.model.WhatHappenedEntity;
import com.accenture.claims.ai.domain.model.WhatHappened;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "cdi", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface WhatHappenedMapper {

    WhatHappened toWhatHappened(WhatHappenedEntity whatHappenedEntity);

    WhatHappenedEntity toWhatHappenedEntity(WhatHappened whatHappened);

    List<WhatHappened> toWhatHappenedList(List<WhatHappenedEntity> whatHappenedEntities);

    List<WhatHappenedEntity> toWhatHappenedEntityList(List<WhatHappened> whatHappenedEntities);
}
