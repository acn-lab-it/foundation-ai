package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.FnolResource;
import com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep.ClaimSubmissionProgressRepository;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.outbound.persistence.repository.PolicyRepositoryAdapter;
import com.accenture.claims.ai.domain.model.ContactChannel;
import com.accenture.claims.ai.domain.model.Policy;
import com.accenture.claims.ai.domain.model.PolicyHolder;
import com.accenture.claims.ai.domain.model.emailParsing.Contacts;
import com.accenture.claims.ai.domain.model.emailParsing.Reporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class WelcomeTool {

    @Inject
    PolicyRepositoryAdapter policyRepositoryAdapter;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    ClaimSubmissionProgressRepository claimSubmissionProgressRepository;

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
        //TODO LanguageHelper.PromptResult promptResult = languageHelper.getPromptWithLanguage(acceptLanguage, "superAgent.welcomePrompt");
        String prompt = """
                Sei HappyClaim, un assistente virtuale che guida l'utente nel registrare dei claim all'interno del sistema Allianz.
                Presentati, saluta e chiedi indirizzo completissimo e data esattissima dell'evento che si vuole segnalare.
                L'utente si chiama {{fullName}}, la sua polizza è la {{policyNumber}} e l'id sessione è {{sessionId}}
                Rispondi in questa lingua {{acceptLanguage}}
                """;

        String systemPrompt = languageHelper.applyVariables(prompt, Map.of(
                "sessionId", sessionId,
                "fullName", fullName,
                "policyNumber", policy.getPolicyNumber(),
                "acceptLanguage", acceptLanguage));

        saveJsonOutput(sessionId, retrievePolicyHolder); //TODO
        return new FnolResource.ChatResponseDto(sessionId, systemPrompt, null);
    }

    private void saveJsonOutput(String sessionId, PolicyHolder policyHolder) {
        var progress = claimSubmissionProgressRepository.findBySessionId(sessionId).getParsingResult();
        Reporter reporter = new Reporter();
        progress.setReporter(reporter);
        reporter.setFirstName(policyHolder.getFirstName());
        reporter.setLastName(policyHolder.getLastName());
        Contacts contacts = new Contacts();
        reporter.setContacts(contacts);
        Optional<ContactChannel> email = policyHolder.getContactChannels().stream().filter(val -> StringUtils.equalsIgnoreCase("EMAIL", val.getCommunicationType())).findFirst();
        Optional<ContactChannel> mobile = policyHolder.getContactChannels().stream().filter(val -> StringUtils.equalsIgnoreCase("MOBILE", val.getCommunicationType())).findFirst();
        email.ifPresent(contactChannel -> contacts.setEmail(contactChannel.getCommunicationDetails()));
        mobile.ifPresent(contactChannel -> contacts.setMobile(contactChannel.getCommunicationDetails()));
        claimSubmissionProgressRepository.upsertBySessionId(sessionId, progress);
    }

}
