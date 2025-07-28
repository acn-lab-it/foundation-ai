package com.accenture.claims.ai.adapter.outbound.rest;

import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test implementation of RestService that returns predefined responses
 * This class is used for testing the SpeechToTextService
 */
@Mock
@ApplicationScoped
public class TestRestService extends RestService {
    
    private String responseToReturn = "{ \"status\": 200, \"body\": { \"text\": \"This is the transcribed text\" } }";
    
    /**
     * Set the response that will be returned by the restApi method
     * @param response The response to return
     */
    public void setResponseToReturn(String response) {
        this.responseToReturn = response;
    }
    
    /**
     * Override the restApi method to return a predefined response
     */
    @Override
    public String restApi(String url, String method, String body) {
        return responseToReturn;
    }
}