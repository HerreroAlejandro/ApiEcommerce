package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.config.ModelMapperConfig;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.*;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.*;
import com.api.crud.services.AdministrativeHistoryService;

import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

class AdministrativeHistoryTests {
    private OrderWorkflowDao orders;
    private CartChangeAuditDao carts;
    private UserModel actor;
    private AdministrativeHistoryService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderWorkflowDao.class);
        carts = mock(CartChangeAuditDao.class);
        UserDao users = mock(UserDao.class);
        actor = new UserModel();
        actor.setId(7L);
        actor.setRoles(Set.of(new Role("ADMIN")));
        when(users.getUserByEmail("staff@example.com")).thenReturn(Optional.of(actor));
        service =
                new AdministrativeHistoryService(
                        orders, carts, users, new ModelMapperConfig().modelMapper());
    }

    @Test
    void systemEventsAndPaginationArePreservedWithoutExposingEntities() {
        var event = new OrderChangeAudit();
        event.setId(30L);
        event.setOrderId(10L);
        event.setAction("EXPIRE");
        event.setPreviousValue("PENDING");
        event.setNewValue("EXPIRED");
        event.setReason("Vencimiento automatico");
        event.setCreatedAt(LocalDateTime.now());
        var page = PageRequest.of(1, 20);
        when(orders.getOrderHistory(10L, page))
                .thenReturn(new PageImpl<>(List.of(event), page, 21));
        var result = service.getOrderHistory("staff@example.com", 10L, page);
        assertEquals(21, result.getTotalElements());
        assertEquals(1, result.getNumber());
        assertNull(result.getContent().get(0).getActorUserId());
        assertEquals("EXPIRED", result.getContent().get(0).getNewValue());
        assertEquals(event.getCreatedAt(), result.getContent().get(0).getCreatedAt());
    }

    @Test
    void deletedCartHistoryUsesTheStoredAuditIdentifierAndSupportsAnEmptyPage() {
        var event = new CartChangeAudit();
        event.setId(40L);
        event.setCartId(12L);
        event.setActorUserId(7L);
        event.setAction("DELETE_CART");
        event.setPreviousAmount(2);
        event.setNewAmount(0);
        event.setReason("Caso excepcional");
        var page = PageRequest.of(0, 20);
        when(carts.getByCartId(12L, page)).thenReturn(new PageImpl<>(List.of(event), page, 1));
        actor.setRoles(Set.of(new Role("SUPPORT")));
        var result = service.getCartHistory("staff@example.com", 12L, page);
        assertEquals("DELETE_CART", result.getContent().get(0).getAction());
        assertEquals(2, result.getContent().get(0).getPreviousAmount());
        assertNull(result.getContent().get(0).getProductId());
        when(carts.getByCartId(99L, page)).thenReturn(Page.empty(page));
        assertTrue(service.getCartHistory("staff@example.com", 99L, page).isEmpty());
    }

    @Test
    void unauthorizedAccountsAndInvalidPagesNeverQueryTheAuditTables() {
        var page = PageRequest.of(0, 20);
        actor.setRoles(Set.of(new Role("CLIENT")));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getOrderHistory("staff@example.com", 10L, page));
        assertThrows(
                ForbiddenOperationException.class,
                () -> service.getCartHistory("staff@example.com", 12L, page));
        actor.setRoles(Set.of(new Role("ADMIN")));
        actor.setStatus(AccountStatus.BLOCKED);
        assertThrows(
                UnauthenticatedException.class,
                () -> service.getCartHistory("staff@example.com", 12L, page));
        actor.setStatus(AccountStatus.ACTIVE);
        assertThrows(
                InvalidRequestException.class,
                () -> service.getOrderHistory("staff@example.com", 10L, PageRequest.of(0, 101)));
        assertThrows(
                InvalidRequestException.class,
                () -> service.getCartHistory("staff@example.com", -1L, page));
        verifyNoInteractions(orders, carts);
    }
}
