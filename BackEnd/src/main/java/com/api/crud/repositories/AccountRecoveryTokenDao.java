package com.api.crud.repositories;

import com.api.crud.models.entity.AccountRecoveryToken;
import com.api.crud.models.entity.UserModel;

import java.util.Optional;

public interface AccountRecoveryTokenDao {

    void save(AccountRecoveryToken recoveryToken);

    Optional<AccountRecoveryToken> findByToken(String token);

    void invalidateTokensByUser(UserModel user);

    void update(AccountRecoveryToken recoveryToken);
}
