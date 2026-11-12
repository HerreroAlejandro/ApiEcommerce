package com.api.crud.repositories;

import com.api.crud.models.entity.EmailVerificationToken;

import java.util.Optional;

public interface EmailVerificationTokenDao {
    Optional<EmailVerificationToken> getByUserId(Long userId);

    Optional<Long> getUserIdByTokenHash(String hash);

    void save(EmailVerificationToken token);
}
