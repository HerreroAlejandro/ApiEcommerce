package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.DTO.CartAdminFilterDTO;
import com.api.crud.controllers.AdminCartController;
import com.api.crud.services.AdministrativeHistoryService;
import com.api.crud.services.CartItemService;
import com.api.crud.services.CartService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

class AdminCartQueryTests {

    private MockMvc mvc;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = mock(CartService.class);
        AdminCartController controller =
                new AdminCartController(
                        service,
                        mock(CartItemService.class),
                        mock(AdministrativeHistoryService.class));
        when(service.getCarts(any(), any())).thenAnswer(call -> Page.empty(call.getArgument(1)));
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                        .build();
    }

    @Test
    void bindsCombinedFiltersAndPagination() throws Exception {
        mvc.perform(
                        get("/admin/cart/getCarts")
                                .param("minTotal", "50000")
                                .param("maxTotal", "100000")
                                .param("active", "true")
                                .param("productId", "8")
                                .param("minProductQuantity", "10")
                                .param("page", "1")
                                .param("size", "20")
                                .param("sort", "total,desc"))
                .andExpect(status().isOk());
        var filter = ArgumentCaptor.forClass(CartAdminFilterDTO.class);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(service).getCarts(filter.capture(), page.capture());
        assertEquals(new BigDecimal("50000"), filter.getValue().getMinTotal());
        assertEquals(8L, filter.getValue().getProductId());
        assertEquals(10, filter.getValue().getMinProductQuantity());
        assertEquals(1, page.getValue().getPageNumber());
        assertEquals(20, page.getValue().getPageSize());
        assertTrue(page.getValue().getSort().getOrderFor("total").isDescending());
    }

    @Test
    void invalidFiltersAreRejectedBeforeService() throws Exception {
        mvc.perform(get("/admin/cart/getCarts").param("minTotal", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get("/admin/cart/getCarts")
                                .param("productId", "8")
                                .param("minProductQuantity", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
