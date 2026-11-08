package com.api.crud.config;

import com.api.crud.models.entity.UserModel;
import com.api.crud.models.enums.AccountStatus;
import com.api.crud.repositories.UserDao;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;

    private final UserDao userDao;

    public JWTFilter(JWTUtil jwtUtil, UserDao userDao) {
        this.jwtUtil = jwtUtil;
        this.userDao = userDao;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            SecurityContextHolder.clearContext();
            try {
                authenticate(header.substring(7));
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        if (!jwtUtil.validateToken(token)) return;
        String email = jwtUtil.extractUsername(token);
        if (email == null || email.isBlank()) return;
        Optional<UserModel> user = userDao.getUserByEmail(email);
        Long version = jwtUtil.extractAuthVersion(token);
        if (user.isEmpty()
                || user.get().getStatus() != AccountStatus.ACTIVE
                || version == null
                || version != user.get().getAuthVersion()) return;
        var authorities =
                jwtUtil.extractRoles(token).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(email, null, authorities));
    }
}
