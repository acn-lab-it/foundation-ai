package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@ApplicationScoped
public class EmailParserTool {

    private static final Pattern ISO_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})Z?");
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    SessionLanguageContext sessionLanguageContext;

    @Inject
    LanguageHelper languageHelper;

    @Inject
    ChatModel chatModel;

    @Inject
    FinalOutputJSONStore finalOutputJSONStore;


    public record MediaItem(String mediaName, String mediaDescription, String mediaType) {
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    @Tool(
        name = "parseEmail",
        value = """
        Task: Extract structured JSON data from input text
        
        You will receive an unstructured input (e.g., an email or free-form text) that contains all the necessary information to populate a JSON object.
        
        Your task is to:
        
        Extract the relevant data.
        
        Populate a single, valid JSON object using the schema provided below.
        
        Return only the final JSON in the output — no explanations or additional text.
        
        JSON Schema
        {
          "type": "object",
          "required": [],
          "properties": {
            "policyNumber": {
              "type": "string",
              "description": "Insurance policy number associated with the claim."
            },
            "reporter": {
              "type": "object",
              "required": ["firstName", "lastName", "contacts"],
              "properties": {
                "firstName": {
                  "type": "string",
                  "description": "First name of the reporter."
                },
                "lastName": {
                  "type": "string",
                  "description": "Last name of the reporter."
                },
                "contacts": {
                  "type": "object",
                  "required": ["email", "mobile"],
                  "properties": {
                    "email": {
                      "type": "string",
                      "format": "email",
                      "description": "Email address of the reporter."
                    },
                    "mobile": {
                      "type": "string",
                      "description": "Mobile phone number of the reporter (international format)."
                    }
                  }
                }
              }
            },
            "incidentDate": {
              "type": "string",
              "format": "date-time",
              "description": "Date and time of the incident in ISO 8601 format (e.g., 2025-11-10T14:00:00Z)."
            },
            "incidentLocation": {
              "type": "string",
              "description": "Address or location where the incident occurred."
            },
            "circumstances": {
              "type": "object",
              "required": ["details", "notes"],
              "description": "Contextual information about the circumstances of the incident.",
              "properties": {
                "details": {
                  "type": "string",
                  "description": "General category of the circumstances (e.g., fire, flood)."
                },
                "notes": {
                  "type": "string",
                  "description": "Freeform description providing more detail about the event."
                }
              }
            },
            "damageDetails": {
              "type": "string",
              "description": "Details about the detected damage, possibly including confidence score."
            }
          }
        }
        
        
        Guidelines
        
        Use ISO 8601 format for dates (incidentDate).
        
        For any field that is missing or unclear in the input, do not invent data — leave it out only if truly absent and not required.
        
        The output must be a single, syntactically valid JSON object, ready to be used by another system.
        
        All required fields must be filled in, if the data is present in the input.
        
        Example input (rawEmail):
        
        Hi, I’m Lukas Baumgartner. My house caught fire on November 10th at 2PM in Vienna, Linzer Str. Some walls are damaged. My insurance number is MTRHHR00026398. Please find attached a photo and a video of the incident. Contact: allianz@test.at
        , +61456677674.
        
        Expected output format:
        {
          "policyNumber": "...",
          ...
        }
        
        
        Input parameter: rawEmail
""")
    public String parseEmail(
            String sessionId,
            String rawEmail
    ) {

        String lang = sessionLanguageContext.getLanguage(sessionId); // fallback interno a 'en'

        // Carica template multilingua dal DB
        LanguageHelper.PromptResult promptResult =
                languageHelper.getPromptWithLanguage(lang, "dateParser.mainPrompt");

        String prompt = languageHelper.applyVariables(
                promptResult.prompt,
                java.util.Map.of(
                        "rawEmail", rawEmail
                )
        );

        String jsonString = chatModel.chat(List.of(
                SystemMessage.from("You convert arbitrary date/time strings to ISO-8601."),
                UserMessage.from(prompt)
        )).aiMessage().text().trim();

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode node;
        try {
            node = (ObjectNode) mapper.readTree(jsonString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing JSON object", e);
        }

        String incidentDate = String.valueOf(node.get("incidentDate"));
        String policyNumber = String.valueOf(node.get("policyNumber"));
        ObjectNode reporter = (ObjectNode) node.get("reporter");
        String incidentLocation = String.valueOf(node.get("incidentLocation"));
        ObjectNode circumstances = (ObjectNode) node.get("circumstances");
        String damageDetails = String.valueOf(node.get("damageDetails"));

        if (incidentDate.isEmpty()) {
            throw new IllegalArgumentException("Incident date must be filled in.");
        }
        if (policyNumber.isEmpty()) {
            throw new IllegalArgumentException("Policy number must be filled in.");
        }
        if (reporter.isEmpty()) {
            throw new IllegalArgumentException("Reporter must be filled in.");
        }
        if (reporter.get("firstName").asText().isEmpty()) {
            throw new IllegalArgumentException("Reporter first name must be filled in.");
        }
        if (reporter.get("lastName").asText().isEmpty()) {
            throw new IllegalArgumentException("Reporter last name must be filled in.");
        }
        if (incidentLocation.isEmpty()) {
            throw new IllegalArgumentException("Incident location must be filled in.");
        }
        if (circumstances.isEmpty()) {
            throw new IllegalArgumentException("Circumstances must be filled in.");
        }
        if (circumstances.get("details").asText().isEmpty()) {
            throw new IllegalArgumentException("Circumstances details must be filled in.");
        }
        if (circumstances.get("notes").asText().isEmpty()) {
            throw new IllegalArgumentException("Circumstances notes must be filled in.");
        }
        if (damageDetails.isEmpty()) {
            throw new IllegalArgumentException("Damage details must be filled in.");
        }
        Matcher m = ISO_PATTERN.matcher(incidentDate);
        if (!m.find()) {
            throw new IllegalArgumentException("LLM did not return ISO-8601: '" + incidentDate + "'");
        }

        ObjectNode patch = mapper.createObjectNode().put("incidentDate", incidentDate);
        patch.put("policyNumber", policyNumber);
        patch.set("reporter", reporter);
        patch.put("incidentLocation", incidentLocation);
        patch.set("circumstances", circumstances);
        patch.put("damageDetails", damageDetails);

        finalOutputJSONStore.put("final_output", sessionId, null, patch);
        return "{\"status\":\"OK\"}";

    }

    private void a(){
        JsonSchema jsonSchema = JsonSchema.builder()
                .name("ClaimForm")
                //.description("Schema for an insurance claim report.")
                .rootElement(JsonObjectSchema.builder()
                        /*.addRequiredProperty*/.addProperty("policyNumber", JsonStringSchema.builder()
                                .description("Insurance policy number associated with the claim.")
                                .build())

                        /*.addRequiredProperty*/.addProperty("reporter", JsonObjectSchema.builder()
                                /*.addRequiredProperty*/.addProperty("firstName", JsonStringSchema.builder()
                                        .description("First name of the reporter.")
                                        .build())
                                /*.addRequiredProperty*/.addProperty("lastName", JsonStringSchema.builder()
                                        .description("Last name of the reporter.")
                                        .build())
                                /*.addRequiredProperty*/.addProperty("contacts", JsonObjectSchema.builder()
                                        /*.addRequiredProperty*/.addProperty("email", JsonStringSchema.builder()
                                                //.format(JsonStringFormat.EMAIL)
                                                .description("Email address of the reporter.")
                                                .build())
                                        /*.addRequiredProperty*/.addProperty("mobile", JsonStringSchema.builder()
                                                .description("Mobile phone number of the reporter (international format).")
                                                .build())
                                        .build())
                                .build())

                        /*.addRequiredProperty*/.addProperty("incidentDate", JsonStringSchema.builder()
                                //.format(JsonStringFormat.DATE_TIME)
                                .description("Date and time of the incident in ISO 8601 format (e.g., 2025-11-10T14:00:00Z).")
                                .build())

                        /*.addRequiredProperty*/.addProperty("incidentLocation", JsonStringSchema.builder()
                                .description("Address or location where the incident occurred.")
                                .build())

                        /*.addRequiredProperty*/.addProperty("circumstances", JsonObjectSchema.builder()
                                .description("Contextual information about the circumstances of the incident.")
                                /*.addRequiredProperty*/.addProperty("details", JsonStringSchema.builder()
                                        .description("General category of the circumstances (e.g., fire, flood).")
                                        .build())
                                /*.addRequiredProperty*/.addProperty("notes", JsonStringSchema.builder()
                                        .description("Freeform description providing more detail about the event.")
                                        .build())
                                .build())

                        /*.addRequiredProperty*/.addProperty("damageDetails", JsonStringSchema.builder()
                                .description("Details about the detected damage, possibly including confidence score.")
                                .build())

                        .build())
                .build();

        // Costruzione dell’oggetto ResponseFormat
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(jsonSchema)
                .build();
    }

}