package com.api.crud.repositories;

import com.api.crud.models.entity.*;

import org.springframework.data.domain.*;

public interface AccountStatusDao {

    UserModel lockUser(Long id);

    void lockToken(AccountRecoveryToken token);

    void save(AccountStatusAudit audit);

    Page<AccountStatusAudit> getHistory(Long userId, Pageable pageable);
}
