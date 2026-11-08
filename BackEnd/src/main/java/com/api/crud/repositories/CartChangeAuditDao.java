package com.api.crud.repositories;

import com.api.crud.models.entity.CartChangeAudit;

public interface CartChangeAuditDao {

    void save(CartChangeAudit audit);
}
