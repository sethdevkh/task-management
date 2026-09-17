package com.sethdevkh.restapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdH2ConsoleGuard implements ApplicationRunner {

    private final boolean h2ConsoleEnabled;

    public ProdH2ConsoleGuard(@Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled) {
        this.h2ConsoleEnabled = h2ConsoleEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (h2ConsoleEnabled) {
            throw new IllegalStateException("H2 console must stay disabled when profile 'prod' is active");
        }
    }
}
