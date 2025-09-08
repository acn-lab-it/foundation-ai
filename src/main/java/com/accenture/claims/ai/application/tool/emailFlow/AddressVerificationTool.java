package com.accenture.claims.ai.application.tool.emailFlow;

import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AddressVerificationTool {
    @Inject
    ChatModel chatModel;

    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject LanguageHelper languageHelper;

    @Tool(
            name = "address_verification_tool",
            value = "Tell whether if the provided address contains the street name and the house number. Parameters: sessionId, adress. Returns: 1 if the adress contains the street name and the house number, 0 otherwise."
    )
    public boolean address_verification_tool(String sessionId, String address) {
        // Basic guard
        if (address == null || address.isBlank()) {
            return false;
        }

        // lingua (se impostata in sessione)
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // prompt system (db o fallback)
        String sys = """
            You are an information extractor. Tell me if the provided address contains the street name and the house number.
            Output MUST be a single number: 1 if the address contains the street name and the house number, 0 otherwise.
            Do not invent values. Do not add anything else: just 1 or 0. No comments. No code fences.
            Address: {{address}}
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.address.verificationPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("address", address));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }


        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(address))).build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        if(raw.equals("1")) {
            return true;
        }
        if(raw.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("LLM did not return 0 or 1: '" + raw + "'");
    }

    @Tool(
            name = "city_verification_tool",
            value = "Tell whether if the provided address contains the city name. Returns: 1 if the adress contains the city name, 0 otherwise."
    )
    public boolean city_verification_tool(String sessionId, String address) {
        // Basic guard
        if (address == null || address.isBlank()) {
            return false;
        }

        // lingua (se impostata in sessione)
        String lang = sessionLanguageContext != null ? sessionLanguageContext.getLanguage(sessionId) : "en";

        // prompt system (db o fallback)
        String sys = """
            You are an information extractor. Tell me if the provided address contains the city name.
            Output MUST be a single number: 1 if the address contains the city name, 0 otherwise.
            Do not invent values. Do not add anything else: just 1 or 0. No comments. No code fences.
            Provided address: {{address}}
            """;

        try {
            if (languageHelper != null) {
                LanguageHelper.PromptResult p =
                        languageHelper.getPromptWithLanguage(lang, "fnol.city.verificationPrompt");
                if (p != null && p.prompt != null && !p.prompt.isBlank()) {
                    sys = languageHelper.applyVariables(p.prompt, Map.of("address", address));
                }
            }
        } catch (Exception ignore) {
            // usa fallback
        }


        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(SystemMessage.from(sys), UserMessage.from(address))).build();

        ChatResponse chatResponse = chatModel.chat(chatRequest);
        String raw = chatResponse.aiMessage().text();

        if(raw.equals("1")) {
            return true;
        }
        if(raw.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("LLM did not return 0 or 1: '" + raw + "'");

    }

}
