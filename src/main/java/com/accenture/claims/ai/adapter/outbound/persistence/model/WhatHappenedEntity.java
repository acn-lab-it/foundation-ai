package com.accenture.claims.ai.adapter.outbound.persistence.model;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@MongoEntity(collection = "whathappened")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class WhatHappenedEntity {

    private String claimClassGroup;
    private String whatHappenedContext;
    private String whatHappenedCode;
}
