package com.sethdevkh.restapi.security;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdAuthGuardTest {

    @Test
    void corsAllowsHttpsOrigin() {
        ProdCorsGuard guard = new ProdCorsGuard(new CorsProperties(List.of("https://app.example.com")));
        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void corsRefusesWildcardAndHttp() {
        assertThrows(IllegalStateException.class,
                () -> new ProdCorsGuard(new CorsProperties(List.of("*"))).run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class,
                () -> new ProdCorsGuard(new CorsProperties(List.of("https://app.example.com", "http://app.example.com")))
                        .run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class,
                () -> new ProdCorsGuard(new CorsProperties(List.of())).run(new DefaultApplicationArguments()));
    }

    @Test
    void jwtSecretMustBeLongEnough() {
        JwtProperties ok = new JwtProperties("prod-only-jwt-secret-key-32bytes!!", java.time.Duration.ofMinutes(15));
        assertDoesNotThrow(() -> new ProdJwtGuard(ok).run(new DefaultApplicationArguments()));
    }

    @Test
    void jwtPropertiesRefuseShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtProperties("too-short", java.time.Duration.ofMinutes(15)));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtProperties("prod-only-jwt-secret-key-32bytes!!", java.time.Duration.ZERO));
    }
}
