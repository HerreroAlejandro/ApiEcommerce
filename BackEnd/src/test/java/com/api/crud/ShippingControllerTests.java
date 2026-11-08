package com.api.crud;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.DTO.*;
import com.api.crud.controllers.OrderController;
import com.api.crud.exceptions.GlobalExceptionHandler;
import com.api.crud.exceptions.InputValidationHandler;
import com.api.crud.services.OrderService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

class ShippingControllerTests {

    private OrderService orders;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        orders = mock(OrderService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new OrderController(orders))
                        .setControllerAdvice(
                                new InputValidationHandler(), new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void incompleteAddressIsRejectedBeforeCheckout() throws Exception {
        mvc.perform(
                        post("/orders/createOrder")
                                .principal(
                                        new UsernamePasswordAuthenticationToken(
                                                "buyer@example.com", null))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"shippingAddress\":{\"recipientName\":\"Cliente\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['shippingAddress.street']").exists());
        verifyNoInteractions(orders);
    }

    @Test
    void digitalCheckoutCanStillOmitTheBody() throws Exception {
        OrderDetailDTO result = new OrderDetailDTO();
        result.setIdOrder(10L);
        when(orders.createMyOrder("buyer@example.com", null)).thenReturn(result);
        mvc.perform(
                        post("/orders/createOrder")
                                .principal(
                                        new UsernamePasswordAuthenticationToken(
                                                "buyer@example.com", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/orders/10"));
    }

    @Test
    void flatRateIsAvailableWithoutConflictingWithOrderIdLookup() throws Exception {
        when(orders.getShippingRate()).thenReturn(new ShippingRateDTO(new BigDecimal("50.00")));
        mvc.perform(get("/orders/shipping-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingCost").value(50.0));
    }
}
