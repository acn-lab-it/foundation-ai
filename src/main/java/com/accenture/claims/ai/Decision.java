package com.accenture.claims.ai;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Decision {
    public enum Action {
        TECHNICAL, ADMIN, BOTH, NONE
    }

    public Action action;
}
