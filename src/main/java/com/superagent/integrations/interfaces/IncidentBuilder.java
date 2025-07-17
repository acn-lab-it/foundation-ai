package com.superagent.integrations.interfaces;

public class IncidentBuilder {
    private Incident draft = new Incident();

    public IncidentBuilder add(Incident partial) {
        // copy values from partial if not null
        if (partial != null) {
            if (partial.getIncidentId() != null) draft = builderFrom(draft).incidentId(partial.getIncidentId()).build();
            if (partial.getIncidentDateTime() != null) draft = builderFrom(draft).incidentDateTime(partial.getIncidentDateTime()).build();
            if (partial.getLocation() != null) draft = builderFrom(draft).location(partial.getLocation()).build();
        }
        return this;
    }

    public Incident preview() {
        return draft;
    }

    public Incident build() {
        return draft; // validation should be added
    }

    private Builder builderFrom(Incident base) {
        Builder b = new Builder();
        b.draft = base;
        return b;
    }

    public static class Builder extends Incident.Builder {
        private Incident draft = new Incident();
        @Override
        public Incident build() {return draft;}
    }
}
