package com.sethdevkh.restapi.security;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdCorsGuard implements ApplicationRunner {

    private final List<String> origins;

    public ProdCorsGuard(CorsProperties properties) {
        this.origins = properties.origins();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (origins.isEmpty()) {
            throw new IllegalStateException("prod CORS_ALLOWED_ORIGINS must be set to the web origin");
        }
        for (String origin : origins) {
            if ("*".equals(origin) || origin.contains("*")) {
                throw new IllegalStateException("prod must not use a wildcard CORS origin");
            }
            if (!origin.startsWith("https://")) {
                throw new IllegalStateException("prod CORS origins must be HTTPS: " + origin);
            }
        }
    }
}
