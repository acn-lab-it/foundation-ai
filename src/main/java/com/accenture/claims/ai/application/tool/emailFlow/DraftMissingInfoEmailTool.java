package com.accenture.claims.ai.application.tool.emailFlow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DraftMissingInfoEmailTool {

    @Inject ChatModel chatModel;
    private static final ObjectMapper M = new ObjectMapper();

    @Tool("""
        Compose a professional email draft asking the reporter to provide missing FNOL details.
        Parameters:
        - sessionId: current session id (string)
        - emailId: optional email id for reference (string, may be null or empty)
        - recipientEmail: the reporter's email to address (string, may be null)
        - missingFieldsJson: a JSON object mapping required keys to null (e.g. {"policyNumber":null,...})
        - needMoreAccidentDetails: boolean, true if both whatHappenedContext and whatHappenedCode are missing
        - locale: language hint like "it" or "en" (string, optional)
        Returns: a single string with the full email draft (Subject + Body).
        """)
    public String draftMissingInfoEmail(String sessionId,
                                        String emailId,
                                        String recipientEmail,
                                        String missingFieldsJson,
                                        boolean needMoreAccidentDetails,
                                        String locale) throws Exception {

        JsonNode missing = M.readTree(missingFieldsJson == null ? "{}" : missingFieldsJson);

        String sys = """
            You are an insurance claims assistant. Write a courteous and professional email asking the customer
            to provide the missing First Notification of Loss (FNOL) details.
            - Be concise and well-structured (subject + greeting + bullet list of missing items + closing).
            - For each missing key, show a short, user-friendly description of WHAT to provide.
            - Keep the tone helpful and polite; avoid legal jargon.
            - If asked (flag provided), also request a brief description of accident dynamics (what happened).
            - Output language should match the locale if provided (e.g., "it" or "en"). Default to English.
            """;

        String user = """
            Context:
            - sessionId: %s
            - emailId: %s
            - recipientEmail: %s
            - locale: %s

            Missing fields (JSON):
            %s

            Need extra accident dynamics details? %s

            Please return a single email draft as plain text, including:
            1) Subject: ...
            2) Body: ...
            """.formatted(
                safe(sessionId),
                safe(emailId),
                safe(recipientEmail),
                safe(locale),
                missing.toPrettyString(),
                String.valueOf(needMoreAccidentDetails)
        );

        ChatResponse resp = chatModel.chat(ChatRequest.builder()
                .messages(java.util.List.of(SystemMessage.from(sys), UserMessage.from(user)))
                .temperature(0.3)
                .maxOutputTokens(700)
                .build());

        return resp.aiMessage().text();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
