package com.accenture.claims.ai.domain.model.emailParsing;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class EmailParsingResult {
    private String id;

    private String emailId;

    private Reporter reporter;

    private String incidentDate;
    private String incidentLocation;
    private String policyNumber;

    private Map<String, Object> otherFields = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getOtherFields() {
        return otherFields;
    }

    @JsonAnySetter
    public void setOtherField(String key, Object value) {
        this.otherFields.put(key, value);
    }
}
