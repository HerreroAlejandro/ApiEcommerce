package com.api.crud.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

@Component
public class JWTUtil {

    private final SecretKey signingKey;

    private final JwtParser parser;

    public JWTUtil(@Value("${JWT_ECO}") String secret) {
        if (secret == null
                || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT_ECO debe contener una clave de al menos 32 bytes");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        parser = Jwts.parserBuilder().setSigningKey(signingKey).build();
    }

    public String generateToken(String username, List<String> roles, long authVersion) {
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .claim("authVersion", authVersion)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 30 * 60 * 1000L))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return claims(token).getSubject();
    }

    public Long extractAuthVersion(String token) {
        return claims(token).get("authVersion", Long.class);
    }

    public boolean isTokenExpired(String token) {
        return claims(token).getExpiration().before(new Date());
    }

    public boolean validateToken(String token) {
        try {
            claims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public List<String> extractRoles(String token) {
        List<?> roles = claims(token).get("roles", List.class);
        if (roles == null || roles.stream().anyMatch(role -> !(role instanceof String))) {
            throw new JwtException("Roles invalidos en el token");
        }
        return roles.stream().map(String.class::cast).toList();
    }

    private Claims claims(String token) {
        return parser.parseClaimsJws(token).getBody();
    }
}
