package com.api.crud;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.controllers.*;
import com.api.crud.services.*;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EndpointValidationTests {

    private MockMvc mvc;

    private UserService users;

    private CartItemService items;

    private ProductService products;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
        items = mock(CartItemService.class);
        products = mock(ProductService.class);
        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "userService", users);
        ProductController productController = new ProductController();
        ReflectionTestUtils.setField(productController, "productService", products);
        mvc =
                MockMvcBuilders.standaloneSetup(
                                userController, new CartItemController(items), productController)
                        .setControllerAdvice(new InputValidationHandler())
                        .build();
    }

    @Test
    void registrationRejectsMissingFieldsAndDoesNotExposePassword() throws Exception {
        mvc.perform(
                        post("/users/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"invalid\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errors.email").exists())
                .andExpect(content().string(Matchers.not(Matchers.containsString("secret"))));
        verifyNoInteractions(users);
    }

    @Test
    void resetRejectsUnmatchedPasswordsBeforeService() throws Exception {
        mvc.perform(
                        patch("/users/password-recovery/reset")
                                .param("token", "token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"newPassword\":\"valid-password\",\"confirmPassword\":\"another-password\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test
    void numericIdsAndQuantitiesAreValidatedBeforeService() throws Exception {
        mvc.perform(get("/products/GetProductById/0")).andExpect(status().isBadRequest());
        mvc.perform(post("/cartItem/addItemToCart").param("productId", "1").param("amount", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(products, items);
    }

    @Test
    void malformedJsonAndParameterTypesReturnBadRequest() throws Exception {
        mvc.perform(post("/users/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/products/GetProductById/not-a-number"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, products);
    }

    @Test
    void validLegacyLoginStillReachesService() throws Exception {
        when(users.login(any())).thenReturn("jwt");
        mvc.perform(
                        post("/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"client@example.com\",\"password\":\"old\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("jwt"));
        verify(users).login(any());
    }
}
