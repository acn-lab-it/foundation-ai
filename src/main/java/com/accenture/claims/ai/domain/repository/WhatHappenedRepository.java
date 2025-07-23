package com.accenture.claims.ai.domain.repository;

import com.accenture.claims.ai.domain.model.WhatHappened;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

public interface WhatHappenedRepository {

    List<WhatHappened> findAllWhatHappenedEvents();
}
