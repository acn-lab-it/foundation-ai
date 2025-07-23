package com.accenture.claims.ai.adapter.outbound.persistence.model;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Getter;

@MongoEntity(collection = "whathappened")
public class WhatHappenedEntity {

    @Getter
    public enum ClaimClassGroup {
        OWNDAMAGE,
        LIABILITY,
        LEGAL;
    }

    public ClaimClassGroup claimClassGroup;
    public String whatHappenedContext;
    public String whatHappenCode;


}
