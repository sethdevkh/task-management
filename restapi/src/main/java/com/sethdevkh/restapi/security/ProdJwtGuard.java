package com.sethdevkh.restapi.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdJwtGuard implements ApplicationRunner {

    private final String secret;

    public ProdJwtGuard(JwtProperties properties) {
        this.secret = properties.secret();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < JwtProperties.MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes in prod");
        }
    }
}
