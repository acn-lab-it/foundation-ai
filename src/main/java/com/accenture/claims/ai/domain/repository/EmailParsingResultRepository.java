package com.accenture.claims.ai.domain.repository;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface EmailParsingResultRepository {

    Optional<EmailParsingResult> findByEmailId(String emailId);

    Optional<EmailParsingResult> findBySessionId(String sessionId);

    List<EmailParsingResult> findAllByEmailId(String emailId);

    Optional<EmailParsingResult> findByEmailIdAndSessionId(String emailId, String sessionId);

    // ---------

    List<EmailParsingResult> getAll();

    void persist(EmailParsingResult emailParsingResult);

    void update(EmailParsingResult emailParsingResult);

    void persistOrUpdate(EmailParsingResult emailParsingResult);

}
