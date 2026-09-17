package com.sethdevkh.restapi.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean enabled, String teamName, SeedUser lead, SeedUser alex, SeedUser bailey) {

    public record SeedUser(String email, String displayName, String password) {
    }
}
