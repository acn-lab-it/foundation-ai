package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.FnolResource;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.outbound.persistence.repository.PolicyRepositoryAdapter;
import com.accenture.claims.ai.domain.model.ContactChannel;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.model.PolicyHolder;
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
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class WelcomeTool {

    @Inject
    PolicyRepositoryAdapter policyRepositoryAdapter;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;

    private final ObjectMapper mapper = new ObjectMapper();

    public FnolResource.ChatResponseDto welcomeMsg(String formPolicyNumber, String formUserEmailAddress, String acceptLanguage, String sessionId) throws BadRequestException {
        Optional<Policy> policyNumber = policyRepositoryAdapter.findByPolicyNumber(formPolicyNumber);
        String fullName = "";
        if (policyNumber.isEmpty()) {
            throw new BadRequestException("{\"error\":\"not found policyNumber\"}");
        }
        Policy policy = policyNumber.get();
        PolicyHolder retrievePolicyHolder = null;
        boolean foundEmailRelatedToPolicy = false;
        for (PolicyHolder policyHolder : policy.getPolicyHolders()) {
            for (ContactChannel contactChannel : policyHolder.getContactChannels()) {
                if (StringUtils.equalsIgnoreCase(contactChannel.getCommunicationType(), "email") && StringUtils.equalsIgnoreCase(formUserEmailAddress, contactChannel.getCommunicationDetails())) {
                    foundEmailRelatedToPolicy = true;
                    fullName = policyHolder.getLastName() + " " + policyHolder.getFirstName();
                    retrievePolicyHolder = policyHolder;
                    break;
                }
            }
        }
        if (!foundEmailRelatedToPolicy) {
            throw new BadRequestException("{\"error\":\"emailAddress not related to policyNumber\"}");
        }
        LanguageHelper.PromptResult promptResult = languageHelper.getPromptWithLanguage(acceptLanguage, "superAgent.welcomePrompt");

        String systemPrompt = languageHelper.applyVariables(promptResult.prompt, Map.of("sessionId", sessionId, "fullName", fullName, "policyNumber", policy.getPolicyNumber()));

        saveJsonOutput(sessionId, policy, retrievePolicyHolder);

        return new FnolResource.ChatResponseDto(sessionId, systemPrompt, null);
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
        patch.set("reporter", mapper.convertValue(policy.getPolicyNumber(), JsonNode.class));

        patch.set("policyNumber", mapper.convertValue(policy.getPolicyNumber(), JsonNode.class));
        patch.set("reporter", reporterNode);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        LocalDateTime dateTime = LocalDateTime.now();
        String formattedDateTime = dateTime.format(formatter);

        patch.set("createdAt", mapper.convertValue(formattedDateTime, JsonNode.class));

        finalOutputJSONStore.put("final_output", sessionId, null, patch);
    }

}
