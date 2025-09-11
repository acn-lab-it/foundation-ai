package com.accenture.claims.ai.domain.model.emailParsing;

import com.accenture.claims.ai.domain.model.AdministrativeCheck;
import com.accenture.claims.ai.domain.model.damage.Circumstances;
import com.accenture.claims.ai.domain.model.damage.Media;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class EmailParsingResult {
    private String id;

    private String emailId;
    private String sessionId;

    private String incidentDate;
    private String incidentLocation;
    private String policyNumber;

    private Reporter reporter;

    private AdministrativeCheck administrativeCheck;
    private Circumstances circumstances;
    private String damageDetails;
    private String policyStatus;
    private List<Media> uploadedMedia;
    private String whatHappenedCode;
    private String whatHappenedContext;

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
