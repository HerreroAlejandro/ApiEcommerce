package com.api.crud;

import com.api.crud.controllers.AdminCartItemController;
import com.api.crud.controllers.CartConflictHandler;
import com.api.crud.services.CartItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminCartItemControllerTests {
    private CartItemService service;
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken actor =
            new UsernamePasswordAuthenticationToken("admin@example.com", null);

    @BeforeEach
    void setUp() {
        service = mock(CartItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminCartItemController(service))
                .setControllerAdvice(new CartConflictHandler()).build();
    }

    @Test
    void rejectsMissingReasonAndInvalidQuantityBeforeCallingService() throws Exception {
        mvc.perform(post("/admin/cart-items").principal(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":7,\"productId\":4,\"amount\":0,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/admin/cart-items/1/amount").principal(actor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void validUpdateUsesAuthenticatedActorAndReturnsNoContent() throws Exception {
        mvc.perform(put("/admin/cart-items/1/amount").principal(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":3,\"reason\":\"Asistencia\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).updateAdminItemAmount("admin@example.com", 1L, 3, "Asistencia");
    }

    @Test
    void concurrentModificationReturnsConflict() throws Exception {
        doThrow(new OptimisticLockingFailureException("concurrent update")).when(service)
                .updateAdminItemAmount("admin@example.com", 1L, 3, "Asistencia");
        mvc.perform(put("/admin/cart-items/1/amount").principal(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":3,\"reason\":\"Asistencia\"}"))
                .andExpect(status().isConflict());
    }
}
