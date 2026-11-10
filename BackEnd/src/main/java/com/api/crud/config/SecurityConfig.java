package com.api.crud.config;

import com.api.crud.exceptions.SecurityProblemHandler;
import com.api.crud.repositories.UserDao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JWTUtil jwtUtil;

    private final UserDao userDao;

    public SecurityConfig(JWTUtil jwtUtil, UserDao userDao) {
        this.jwtUtil = jwtUtil;
        this.userDao = userDao;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SecurityProblemHandler problems = new SecurityProblemHandler();
        http.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(problems)
                                        .accessDeniedHandler(problems))
                .authorizeHttpRequests(
                        authorizeRequests ->
                                authorizeRequests
                                        .requestMatchers("/users/register")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/payments/mercadopago/webhook")
                                        .permitAll()
                                        .requestMatchers("/payments/**")
                                        .denyAll()
                                        .requestMatchers("/users/login")
                                        .permitAll()
                                        .requestMatchers("/users/account-recovery/**")
                                        .permitAll()
                                        .requestMatchers("/users/password-recovery/**")
                                        .permitAll()
                                        .requestMatchers("/admin/products/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/admin/payments/*/refund")
                                        .hasAuthority("ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/admin/payments/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/admin/digital-deliveries/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/digital-deliveries/**")
                                        .authenticated()
                                        .requestMatchers("/admin/cart/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/admin/cart-items/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/admin/orders/*/payment-status")
                                        .hasAuthority("ADMIN")
                                        .requestMatchers(
                                                HttpMethod.GET, "/admin/orders/*/physical-return")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers(
                                                "/admin/orders/*/physical-return",
                                                "/admin/orders/*/physical-return/**")
                                        .hasAuthority("ADMIN")
                                        .requestMatchers("/admin/orders/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers(HttpMethod.GET, "/admin/order-items/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/admin/order-items/**")
                                        .denyAll()
                                        .requestMatchers(HttpMethod.GET, "/order-items/**")
                                        .authenticated()
                                        .requestMatchers("/order-items/**")
                                        .denyAll()
                                        .requestMatchers("/orders", "/orders/**")
                                        .authenticated()
                                        .requestMatchers(
                                                HttpMethod.GET, "/admin/users/*/status-history")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers("/admin/**")
                                        .hasAuthority("ADMIN")
                                        .requestMatchers("/support/**")
                                        .hasAnyAuthority("ADMIN", "SUPPORT")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/products/ShowProducts",
                                                "/products/GetProductById/{id}")
                                        .permitAll()
                                        .requestMatchers("/products/**")
                                        .denyAll()
                                        .requestMatchers("/cart", "/cart/**")
                                        .authenticated()
                                        .requestMatchers("/cartItem/**")
                                        .authenticated()
                                        .anyRequest()
                                        .authenticated())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new JWTFilter(jwtUtil, userDao),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
