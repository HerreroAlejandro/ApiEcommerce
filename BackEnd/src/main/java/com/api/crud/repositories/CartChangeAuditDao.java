package com.api.crud.repositories;

import com.api.crud.models.entity.CartChangeAudit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CartChangeAuditDao {

    void save(CartChangeAudit audit);

    Page<CartChangeAudit> getByCartId(Long cartId, Pageable pageable);
}
