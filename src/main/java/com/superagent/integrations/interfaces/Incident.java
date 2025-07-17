package com.superagent.integrations.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Java representation of Incident.js schema.
 * Only a subset of fields is included for brevity.
 */
public class Incident {
    @NotBlank
    private String incidentId;

    @NotBlank
    private String incidentDateTime;

    @NotNull
    private Location location;

    private Incident() {}

    public String getIncidentId() {return incidentId;}
    public String getIncidentDateTime() {return incidentDateTime;}
    public Location getLocation() {return location;}

    public static Builder builder() {return new Builder();}

    public static class Builder {
        private final Incident draft = new Incident();

        public Builder incidentId(String id) {draft.incidentId = id; return this;}
        public Builder incidentDateTime(String dt) {draft.incidentDateTime = dt; return this;}
        public Builder location(Location loc) {draft.location = loc; return this;}
        public Incident build() {return draft;}
    }

    public static class Location {
        @NotBlank public String city;
        @NotBlank public String countryCode;
        @NotBlank public String streetName;
        @NotBlank public String postalCode;
    }
}
