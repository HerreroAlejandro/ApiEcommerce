package com.api.crud.repositories;

import com.api.crud.models.entity.PasswordResetToken;
import com.api.crud.models.entity.UserModel;

import java.util.Optional;

public interface PasswordResetTokenDao {

    void save(PasswordResetToken resetToken);

    Optional<PasswordResetToken> findByToken(String token);

    void invalidateTokensByUser(UserModel user);

    void update(PasswordResetToken resetToken);
}
