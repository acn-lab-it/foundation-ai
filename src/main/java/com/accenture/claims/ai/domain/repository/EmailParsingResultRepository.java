package com.accenture.claims.ai.domain.repository;

import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;

import java.util.List;
import java.util.Optional;

public interface EmailParsingResultRepository {

    Optional<EmailParsingResult> findByEmailId(String emailId);

    List<EmailParsingResult> getAll();

    void save(EmailParsingResult emailParsingResult);

}
