package com.sethdevkh.restapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdH2ConsoleGuardTest {

    @Test
    void allowsBootWhenConsoleDisabled() {
        ProdH2ConsoleGuard guard = new ProdH2ConsoleGuard(false);
        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void refusesBootWhenConsoleEnabled() {
        ProdH2ConsoleGuard guard = new ProdH2ConsoleGuard(true);
        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments()));
    }
}
