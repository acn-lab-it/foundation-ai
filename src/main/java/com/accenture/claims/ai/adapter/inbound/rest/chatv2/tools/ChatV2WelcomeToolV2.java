package com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.outbound.persistence.repository.PolicyRepositoryAdapter;
import com.accenture.claims.ai.domain.model.ContactChannel;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.model.PolicyHolder;
import com.accenture.claims.ai.adapter.inbound.rest.chatv2.ChatV2Resource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.lang.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ChatV2WelcomeToolV2 {

    @Inject
    PolicyRepositoryAdapter policyRepositoryAdapter;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private final ObjectMapper mapper = new ObjectMapper();

    public ChatV2Resource.ChatResponseDto welcomeMsg(String formPolicyNumber, String formUserEmailAddress, String acceptLanguage, String sessionId) throws BadRequestException {
        System.out.println("=== ChatV2WelcomeToolV2 Debug ===");
        System.out.println("Searching for policy: '" + formPolicyNumber + "'");
        System.out.println("With email: '" + formUserEmailAddress + "'");
        
        Optional<Policy> policyNumber = policyRepositoryAdapter.findByPolicyNumber(formPolicyNumber);
        String fullName = "";
        if (policyNumber.isEmpty()) {
            System.out.println("Policy NOT FOUND in database");
            throw new BadRequestException("{\"error\":\"not found policyNumber\"}");
        }
        System.out.println("Policy FOUND: " + policyNumber.get().getPolicyNumber());
        Policy policy = policyNumber.get();
        PolicyHolder retrievePolicyHolder = null;
        boolean foundEmailRelatedToPolicy = false;
        
        System.out.println("Policy has " + policy.getPolicyHolders().size() + " policy holders");
        for (PolicyHolder policyHolder : policy.getPolicyHolders()) {
            System.out.println("Checking policy holder: " + policyHolder.getFirstName() + " " + policyHolder.getLastName());
            System.out.println("Policy holder has " + policyHolder.getContactChannels().size() + " contact channels");
            for (ContactChannel contactChannel : policyHolder.getContactChannels()) {
                System.out.println("Contact type: '" + contactChannel.getCommunicationType() + "', details: '" + contactChannel.getCommunicationDetails() + "'");
                if (StringUtils.equalsIgnoreCase(contactChannel.getCommunicationType(), "email") && StringUtils.equalsIgnoreCase(formUserEmailAddress, contactChannel.getCommunicationDetails())) {
                    foundEmailRelatedToPolicy = true;
                    fullName = policyHolder.getLastName() + " " + policyHolder.getFirstName();
                    retrievePolicyHolder = policyHolder;
                    System.out.println("EMAIL MATCH FOUND!");
                    break;
                }
            }
        }
        if (!foundEmailRelatedToPolicy) {
            System.out.println("EMAIL NOT FOUND in any policy holder");
            throw new BadRequestException("{\"error\":\"emailAddress not related to policyNumber\"}");
        }
        LanguageHelper.PromptResult promptResult = languageHelper.getPromptWithLanguage(acceptLanguage, "superAgent.welcomePrompt");

        String systemPrompt = languageHelper.applyVariables(promptResult.prompt, Map.of("sessionId", sessionId, "fullName", fullName, "policyNumber", policy.getPolicyNumber()));

        saveJsonOutput(sessionId, policy, retrievePolicyHolder);

        // Crea stepperData per il welcome
        List<ChatV2Resource.StepItem> stepList = new ArrayList<>();
        stepList.add(new ChatV2Resource.StepItem("quando", false));
        stepList.add(new ChatV2Resource.StepItem("cosaSuccesso", false));
        stepList.add(new ChatV2Resource.StepItem("documenti", false));
        ChatV2Resource.StepperData stepperData = new ChatV2Resource.StepperData(stepList, 0);
        
        return new ChatV2Resource.ChatResponseDto(sessionId, systemPrompt, null, "welcome", stepperData);
    }

    private void saveJsonOutput(String sessionId, Policy policy, PolicyHolder policyHolder) {
        mapper.registerModule(new JavaTimeModule());

        ObjectNode patch = mapper.createObjectNode();

        ObjectNode reporterNode = mapper.createObjectNode();
        reporterNode.set("firstName", mapper.convertValue(policyHolder.getFirstName(), JsonNode.class));
        reporterNode.set("lastName", mapper.convertValue(policyHolder.getLastName(), JsonNode.class));

        ObjectNode contactNode = mapper.createObjectNode();
        Optional<ContactChannel> email = policyHolder.getContactChannels().stream().filter(val -> StringUtils.equalsIgnoreCase("EMAIL", val.getCommunicationType())).findFirst();
        Optional<ContactChannel> mobile = policyHolder.getContactChannels().stream().filter(val -> StringUtils.equalsIgnoreCase("MOBILE", val.getCommunicationType())).findFirst();
        email.ifPresent(contactChannel -> contactNode.set("email", mapper.convertValue(contactChannel.getCommunicationDetails(), JsonNode.class)));
        mobile.ifPresent(contactChannel -> contactNode.set("mobile", mapper.convertValue(contactChannel.getCommunicationDetails(), JsonNode.class)));

        reporterNode.set("contacts", contactNode);
        patch.set("reporter", reporterNode);

        patch.set("policyNumber", mapper.convertValue(policy.getPolicyNumber(), JsonNode.class));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        LocalDateTime dateTime = LocalDateTime.now();
        String formattedDateTime = dateTime.format(formatter);

        patch.set("createdAt", mapper.convertValue(formattedDateTime, JsonNode.class));

        finalOutputJSONStore.put("final_output", sessionId, null, patch);
    }
}
