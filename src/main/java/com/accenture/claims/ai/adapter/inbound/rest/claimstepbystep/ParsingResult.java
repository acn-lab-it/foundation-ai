package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import com.accenture.claims.ai.domain.model.AdministrativeCheck;
import com.accenture.claims.ai.domain.model.damage.Circumstances;
import com.accenture.claims.ai.domain.model.damage.Media;
import com.accenture.claims.ai.domain.model.emailParsing.Reporter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ParsingResult {
    private String id;

    //Welcome step
    private String email;
    private String sessionId;
    private Reporter reporter;
    private String policyNumber;
    private AdministrativeCheck administrativeCheck;

    private String incidentDate;
    private String incidentTime;
    private String incidentCountry;
    private String incidentCity;
    private String incidentStreet;
    private String incidentStreetNumber;

    private String policyStatus;

    // Step 1 WHAT
    private String whatHappenedCode;
    private String whatHappenedContext;
    private Circumstances circumstances;
    private String damageDetails;


    private List<Media> uploadedMedia;

    private Map<String, Object> otherFields = new HashMap<>();

    private String formattedAddress; //TODO proper object, not a stringified json

    @JsonAnyGetter
    public Map<String, Object> getOtherFields() {
        return otherFields;
    }

    @JsonAnySetter
    public void setOtherField(String key, Object value) {
        this.otherFields.put(key, value);
    }


}
