package com.accenture.claims.ai.adapter.outbound.persistence.repository;

import com.accenture.claims.ai.adapter.outbound.persistence.mapper.WhatHappenedMapper;
import com.accenture.claims.ai.adapter.outbound.persistence.model.WhatHappenedEntity;
import com.accenture.claims.ai.domain.model.WhatHappened;
import com.accenture.claims.ai.domain.repository.WhatHappenedRepository;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;


@ApplicationScoped
@Priority(20)
public class WhatHappenedRepositoryAdapter implements WhatHappenedRepository, PanacheMongoRepository<WhatHappenedEntity> {

    private final WhatHappenedMapper whatHappenedMapper;

    public WhatHappenedRepositoryAdapter(WhatHappenedMapper whatHappenedMapper) {
        this.whatHappenedMapper = whatHappenedMapper;
    }

    @Tool("Find all whathappened events")
    @Override
    public List<WhatHappened> findAllWhatHappenedEvents() {
        return whatHappenedMapper.toWhatHappenedList(listAll());
    }
}
