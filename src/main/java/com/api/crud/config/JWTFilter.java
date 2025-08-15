package com.api.crud.config;

import com.api.crud.models.entity.UserModel;
import com.api.crud.repositories.UserDao;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;

    private UserDao userDao;

    public JWTFilter(JWTUtil jwtUtil, UserDao userDao) {
        this.jwtUtil = jwtUtil;
        this.userDao = userDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);
        if (token != null && jwtUtil.validateToken(token)) {
            String email = jwtUtil.extractUsername(token);

            Optional<UserModel> optionalUser = userDao.findUserByEmail(email);

            if(optionalUser.isPresent() && optionalUser.get().isActive()) {
                List<String> roles = jwtUtil.extractRoles(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Si usuario no existe o está inactivo
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String token = null;
        String bearerToken = request.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }
        return token;
    }
}
