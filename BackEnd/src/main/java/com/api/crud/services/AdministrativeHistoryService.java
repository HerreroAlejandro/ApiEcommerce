package com.api.crud.services;

import com.api.crud.DTO.CartHistoryResponseDTO;
import com.api.crud.DTO.OrderHistoryResponseDTO;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.UserModel;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.*;
import com.api.crud.validation.InputRules;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdministrativeHistoryService {
    private final OrderWorkflowDao orders;
    private final CartChangeAuditDao carts;
    private final UserDao users;
    private final ModelMapper mapper;

    public AdministrativeHistoryService(
            OrderWorkflowDao orders, CartChangeAuditDao carts, UserDao users, ModelMapper mapper) {
        this.orders = orders;
        this.carts = carts;
        this.users = users;
        this.mapper = mapper;
    }

    private void validate(String email, Long id, Pageable pageable) {
        UserModel actor = users.getUserByEmail(email).orElseThrow(UnauthenticatedException::new);
        if (actor.getStatus() != AccountStatus.ACTIVE) throw new UnauthenticatedException();
        if (!actor.getRoleNames().contains("ADMIN") && !actor.getRoleNames().contains("SUPPORT"))
            throw new ForbiddenOperationException();
        if (id == null || id <= 0) throw new InvalidRequestException("Identificador invalido");
        InputRules.page(pageable);
    }

    public Page<OrderHistoryResponseDTO> getOrderHistory(
            String email, Long orderId, Pageable pageable) {
        validate(email, orderId, pageable);
        return orders.getOrderHistory(orderId, pageable)
                .map(audit -> mapper.map(audit, OrderHistoryResponseDTO.class));
    }

    public Page<CartHistoryResponseDTO> getCartHistory(
            String email, Long cartId, Pageable pageable) {
        validate(email, cartId, pageable);
        // Audits remain readable even when the cart itself was deleted.
        return carts.getByCartId(cartId, pageable)
                .map(audit -> mapper.map(audit, CartHistoryResponseDTO.class));
    }
}
