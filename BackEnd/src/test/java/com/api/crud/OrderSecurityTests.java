package com.api.crud;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.config.*;
import com.api.crud.controllers.*;
import com.api.crud.models.entity.UserModel;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.PasswordResetTokenDao;
import com.api.crud.repositories.RoleDao;
import com.api.crud.repositories.UserDao;
import com.api.crud.services.AccountStatusService;
import com.api.crud.services.EmailService;
import com.api.crud.services.OrderItemService;
import com.api.crud.services.OrderService;
import com.api.crud.services.UserService;

import org.junit.jupiter.api.*;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.*;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.*;

class OrderSecurityTests {

    private AnnotationConfigWebApplicationContext context;

    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @EnableSpringDataWebSupport
    static class Config {

        @Bean
        JWTUtil jwtUtil() {
            JWTUtil jwt = mock(JWTUtil.class);
            when(jwt.validateToken(anyString())).thenReturn(true);
            when(jwt.extractAuthVersion(anyString())).thenReturn(0L);
            when(jwt.extractUsername(anyString())).thenReturn("actor@example.com");
            when(jwt.extractRoles("client")).thenReturn(List.of("CLIENT"));
            when(jwt.extractRoles("support")).thenReturn(List.of("SUPPORT"));
            when(jwt.extractRoles("admin")).thenReturn(List.of("ADMIN"));
            return jwt;
        }

        @Bean
        UserDao userDao() {
            UserDao dao = mock(UserDao.class);
            UserModel user = new UserModel();
            user.setId(7L);
            user.setStatus(AccountStatus.ACTIVE);
            when(dao.getUserByEmail("actor@example.com")).thenReturn(Optional.of(user));
            return dao;
        }

        @Bean
        OrderService orderService() {
            return mock(OrderService.class);
        }

        @Bean
        OrderController orderController(OrderService service) {
            return new OrderController(service);
        }

        @Bean
        AdminOrderController adminOrderController(OrderService service) {
            return new AdminOrderController(service);
        }

        @Bean
        OrderItemService orderItemService() {
            return mock(OrderItemService.class);
        }

        @Bean
        OrderItemController orderItemController(OrderItemService service) {
            return new OrderItemController(service);
        }

        @Bean
        AdminOrderItemController adminOrderItemController(OrderItemService service) {
            return new AdminOrderItemController(service);
        }

        @Bean
        AccountStatusService accountStatusService() {
            return mock(AccountStatusService.class);
        }

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        RoleDao roleDao() {
            return mock(RoleDao.class);
        }

        @Bean
        PasswordResetTokenDao passwordResetTokenDao() {
            return mock(PasswordResetTokenDao.class);
        }

        @Bean
        ModelMapper modelMapper() {
            return new ModelMapper();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        EmailService emailService() {
            return new EmailService();
        }

        @Bean
        JavaMailSender mailSender() {
            return mock(JavaMailSender.class);
        }

        @Bean
        AdminUserController adminUserController() {
            return new AdminUserController();
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class, SecurityConfig.class);
        context.refresh();
        mvc =
                MockMvcBuilders.webAppContextSetup(context)
                        .addFilters(context.getBean(FilterChainProxy.class))
                        .build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void clientCannotReadAdminOrdersAndAnonymousCannotReadOwnOrders() throws Exception {
        mvc.perform(get("/admin/orders").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/orders")).andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(OrderService.class));
    }

    @Test
    void supportCanReadButOnlyAdminCanManuallyChangePayment() throws Exception {
        mvc.perform(get("/admin/orders/getOrders").header("Authorization", "Bearer support"))
                .andExpect(status().isOk());
        String body = "{\"status\":\"PAID\",\"reason\":\"Pago recibido\"}";
        mvc.perform(
                        patch("/admin/orders/10/payment-status")
                                .header("Authorization", "Bearer support")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/admin/orders/10/payment-status")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void orderItemReadsRequireAuthenticationAndAdminReadsRequireStaff() throws Exception {
        mvc.perform(get("/order-items/1")).andExpect(status().isForbidden());
        mvc.perform(get("/order-items/1").header("Authorization", "Bearer client"))
                .andExpect(status().isOk());
        verify(context.getBean(OrderItemService.class)).getMyItem("actor@example.com", 1L);
        mvc.perform(get("/admin/order-items/1").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/order-items/1").header("Authorization", "Bearer support"))
                .andExpect(status().isOk());
        mvc.perform(delete("/admin/order-items/1").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountBlockingAndHistoryRespectRoleBoundaries() throws Exception {
        String body = "{\"reason\":\"Revision de seguridad\"}";
        mvc.perform(
                        patch("/admin/users/7/block")
                                .header("Authorization", "Bearer support")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/admin/users/7/block")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNoContent());
        verify(context.getBean(AccountStatusService.class))
                .block("actor@example.com", 7L, "Revision de seguridad");
        mvc.perform(get("/admin/users/7/status-history").header("Authorization", "Bearer support"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/users/7/status-history").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveAccountsCannotUsePreviouslyIssuedTokens() throws Exception {
        UserDao dao = context.getBean(UserDao.class);
        UserModel user = new UserModel();
        user.setId(7L);
        user.setStatus(AccountStatus.BLOCKED);
        when(dao.getUserByEmail("actor@example.com")).thenReturn(Optional.of(user));
        mvc.perform(get("/orders").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
        user.setStatus(AccountStatus.DEACTIVATED);
        mvc.perform(get("/orders").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(OrderService.class));
    }

    @Test
    void legacyItemMutationsRemainBlockedEvenForAdmin() throws Exception {
        mvc.perform(
                        put("/order-items/1/increase")
                                .param("increment", "2")
                                .header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }
}
