package com.api.crud;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.DTO.PaymentRefundResponseDTO;
import com.api.crud.config.*;
import com.api.crud.config.ModelMapperConfig;
import com.api.crud.controllers.*;
import com.api.crud.models.entity.UserModel;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.models.enums.PaymentRefundStatus;
import com.api.crud.repositories.AccountRecoveryTokenDao;
import com.api.crud.repositories.PasswordResetTokenDao;
import com.api.crud.repositories.RoleDao;
import com.api.crud.repositories.UserDao;
import com.api.crud.services.AccountStatusService;
import com.api.crud.services.DigitalDeliveryService;
import com.api.crud.services.DigitalFileGateway;
import com.api.crud.services.EmailService;
import com.api.crud.services.OrderItemService;
import com.api.crud.services.OrderService;
import com.api.crud.services.PaymentRefundService;
import com.api.crud.services.PaymentService;
import com.api.crud.services.PhysicalReturnService;
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
        PhysicalReturnService physicalReturnService() {
            return mock(PhysicalReturnService.class);
        }

        @Bean
        AdminPhysicalReturnController adminPhysicalReturnController() {
            return new AdminPhysicalReturnController(physicalReturnService());
        }

        @Bean
        PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        PaymentController paymentController() {
            return new PaymentController(paymentService());
        }

        @Bean
        AdminPaymentController adminPaymentController() {
            return new AdminPaymentController(paymentService(), paymentRefundService());
        }

        @Bean
        PaymentRefundService paymentRefundService() {
            PaymentRefundService service = mock(PaymentRefundService.class);
            PaymentRefundResponseDTO response = new PaymentRefundResponseDTO();
            response.setStatus(PaymentRefundStatus.REQUESTED);
            when(service.refund(anyString(), anyLong(), anyString())).thenReturn(response);
            return service;
        }

        @Bean
        MercadoPagoWebhookController mercadoPagoWebhookController() {
            return new MercadoPagoWebhookController(paymentService());
        }

        @Bean
        DigitalDeliveryService digitalDeliveryService() {
            return mock(DigitalDeliveryService.class);
        }

        @Bean
        DigitalDeliveryController digitalDeliveryController() {
            return new DigitalDeliveryController(
                    digitalDeliveryService(), mock(DigitalFileGateway.class));
        }

        @Bean
        AdminDigitalDeliveryController adminDigitalDeliveryController() {
            return new AdminDigitalDeliveryController(digitalDeliveryService());
        }

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
        AccountRecoveryTokenDao accountRecoveryTokenDao() {
            return mock(AccountRecoveryTokenDao.class);
        }

        @Bean
        ModelMapper modelMapper() {
            return new ModelMapperConfig().modelMapper();
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
        clearInvocations(context.getBean(OrderService.class));
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
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized());
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
        mvc.perform(get("/order-items/1")).andExpect(status().isUnauthorized());
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
                .andExpect(status().isUnauthorized());
        user.setStatus(AccountStatus.DEACTIVATED);
        mvc.perform(get("/orders").header("Authorization", "Bearer client"))
                .andExpect(status().isUnauthorized());
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

    @Test
    void digitalDeliveryRoutesRequireOwnershipFlowAndStaffForReopening() throws Exception {
        mvc.perform(get("/digital-deliveries/20/download")).andExpect(status().isUnauthorized());
        String reason = "{\"reason\":\"Conexion interrumpida\"}";
        mvc.perform(
                        post("/admin/digital-deliveries/20/reopen")
                                .header("Authorization", "Bearer client")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reason))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/admin/digital-deliveries/20/reopen")
                                .header("Authorization", "Bearer support")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reason))
                .andExpect(status().isNoContent());
        mvc.perform(get("/orders/10/digital-deliveries").header("Authorization", "Bearer client"))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/admin/digital-deliveries/order/10")
                                .header("Authorization", "Bearer support"))
                .andExpect(status().isOk());
    }

    @Test
    void shipmentManagementRequiresStaffAndValidatedTracking() throws Exception {
        String body =
                "{\"carrier\":\"Carrier\",\"trackingNumber\":\"TRACK123\",\"reason\":\"Despacho\"}";
        mvc.perform(
                        patch("/admin/orders/10/shipment")
                                .header("Authorization", "Bearer client")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        patch("/admin/orders/10/shipment")
                                .header("Authorization", "Bearer support")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isNoContent());
        mvc.perform(
                        patch("/admin/orders/10/shipment")
                                .header("Authorization", "Bearer support")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Despacho\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void physicalReturnWritesRequireAdminAndReceiptQuantitiesAreValidated() throws Exception {
        String path = "/admin/orders/10/physical-return";
        for (String role : List.of("client", "support")) {
            for (String suffix : List.of("", "/receive", "/waive")) {
                mvc.perform(
                                post(path + suffix)
                                        .header("Authorization", "Bearer " + role)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"reason\":\"Devolver\"}"))
                        .andExpect(status().isForbidden());
            }
        }
        mvc.perform(get(path).header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", "Bearer support")).andExpect(status().isOk());
        mvc.perform(
                        post(path)
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Devolver\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post(path + "/receive")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Recepcion\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(path + "/receive")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"reason\":\"Recepcion\",\"items\":[{\"orderItemId\":20,\"receivedQuantity\":2,\"restockedQuantity\":1}]}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post(path + "/waive")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Sin retorno por excepcion\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refundRequestsAndTheirDetailsAreRestrictedToAdminAndRequireAReason() throws Exception {
        for (String role : List.of("client", "support")) {
            mvc.perform(
                            post("/admin/payments/5/refund")
                                    .header("Authorization", "Bearer " + role)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"reason\":\"Devolver\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/admin/payments/5/refund").header("Authorization", "Bearer " + role))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(
                        post("/admin/payments/5/refund")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/admin/payments/5/refund")
                                .header("Authorization", "Bearer admin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Pago duplicado\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/admin/payments/5/refund").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void paymentRoutesRequireAuthenticationAndOnlyWebhookPostIsPublic() throws Exception {
        mvc.perform(post("/orders/10/checkout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/orders/10/checkout").header("Authorization", "Bearer client"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/orders/10/payments").header("Authorization", "Bearer support"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/payments/review").header("Authorization", "Bearer support"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/payments/review").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/admin/payment-checkouts/ORDTST123/sync")
                                .header("Authorization", "Bearer support"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/admin/payment-checkouts/ORDTST123/sync")
                                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/payments/mercadopago/webhook")
                                .param("type", "order")
                                .param("data.id", "123"))
                .andExpect(status().isOk());
        mvc.perform(get("/payments/mercadopago/webhook")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticationAndAuthorizationUseTheSameProblemFormat() throws Exception {
        mvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value("/orders"));
        mvc.perform(get("/admin/orders").header("Authorization", "Bearer client"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        when(context.getBean(JWTUtil.class).validateToken("invalid")).thenReturn(false);
        mvc.perform(get("/orders").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void databaseFailureDuringAuthenticationIsNotReportedAsInvalidCredentials() throws Exception {
        when(context.getBean(UserDao.class).getUserByEmail("actor@example.com"))
                .thenThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "SQL secret"));
        mvc.perform(get("/orders").header("Authorization", "Bearer client"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        verifyNoInteractions(context.getBean(OrderService.class));
    }
}
