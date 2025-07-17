package com.accenture.claims.ai;

import com.accenture.claims.ai.repository.AdminCoverageRepository;
import com.accenture.claims.ai.repository.TechCoverageRepository;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
//@RegisterAiService(tools = {AdminCoverageRepository.class, TechCoverageRepository.class})
public interface CoverageCheckAgent {

    @SystemMessage("""
        You are a claims assistant that decides which repository class to call based on the user's request.
        Choose between:
        - TECHNICAL for technical coverage
        - ADMIN for administrative validation
        - BOTH for both checks
        - NONE in case you're the prompt does not relate to coverages
        """)
    @UserMessage("User says: {prompt}")
    Decision detectCoverage(String prompt);


    @SystemMessage("""
        You are a claims assistant that understands which policy number and what happened code are provided by
        user prompt: {prompt}.
        Answer with a dto:
        - the policy number in the 'policyNumber' key
        - the what happened code in the 'whc' key
        """)
    @UserMessage("User says: {prompt}")
    UserInput detectUserInput(String prompt);


}
