package com.accenture.claims.ai.domain.model;

import com.accenture.claims.ai.adapter.outbound.persistence.model.WhatHappenedEntity;
import lombok.Getter;

public class WhatHappened {
    @Getter
    public enum ClaimClassGroup {
        OWNDAMAGE,
        LIABILITY,
        LEGAL;
    }

    public WhatHappenedEntity.ClaimClassGroup claimClassGroup;
    public String whatHappenedContext;
    public String whatHappenCode;

}
