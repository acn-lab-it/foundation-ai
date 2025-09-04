package com.accenture.claims.ai.adapter.outbound.persistence.repository;

import com.accenture.claims.ai.adapter.outbound.persistence.mapper.EmailParsingResultMapper;
import com.accenture.claims.ai.adapter.outbound.persistence.model.emailParsing.EmailParsingResultEntity;
import com.accenture.claims.ai.domain.model.emailParsing.EmailParsingResult;
import com.accenture.claims.ai.domain.repository.EmailParsingResultRepository;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
@Priority(20)
public class EmailParsingResultRepositoryAdapter implements EmailParsingResultRepository, PanacheMongoRepository<EmailParsingResultEntity> {


    private final EmailParsingResultMapper emailParsingResultMapper;

    public EmailParsingResultRepositoryAdapter(EmailParsingResultMapper emailParsingResultMapper) {
        this.emailParsingResultMapper = emailParsingResultMapper;
    }


    public Optional<EmailParsingResult> findByEmailId(String emailId) {
        return find("emailId = ?1", emailId).firstResultOptional().map(emailParsingResultMapper::toEmailParsingResult);
    }

    @Override
    public List<EmailParsingResult> getAll() {
        return findAll()
                .stream()
                .map(emailParsingResultMapper::toEmailParsingResult)
                .collect(Collectors.toList());
    }

    @Override
    public void persist(EmailParsingResult emailParsingResult) {
        persist(emailParsingResultMapper.toEmailParsingResultEntity(emailParsingResult));
    }

    @Override
    public void update(EmailParsingResult emailParsingResult) {
        update(emailParsingResultMapper.toEmailParsingResultEntity(emailParsingResult));
    }

}
