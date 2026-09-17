package com.sethdevkh.restapi.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    static final String CLAIM_USER_ID = "uid";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_DISPLAY_NAME = "displayName";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(User user) {
        Instant now = Instant.now();
        return createToken(user, now, now.plus(properties.ttl()));
    }

    public String createToken(User user, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_DISPLAY_NAME, user.getDisplayName())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number uid = claims.get(CLAIM_USER_ID, Number.class);
            String roleName = claims.get(CLAIM_ROLE, String.class);
            String displayName = claims.get(CLAIM_DISPLAY_NAME, String.class);
            String email = claims.getSubject();
            if (uid == null || roleName == null || displayName == null || email == null || email.isBlank()) {
                return Optional.empty();
            }
            Role role = Role.valueOf(roleName);
            return Optional.of(new AuthenticatedUser(uid.longValue(), email, displayName, role));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
