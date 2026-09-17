package com.sethdevkh.restapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProdSchemaGuardTest {

    @Test
    void allowsNoneAndValidate() {
        assertDoesNotThrow(() -> new ProdSchemaGuard("none").run(new DefaultApplicationArguments()));
        assertDoesNotThrow(() -> new ProdSchemaGuard("validate").run(new DefaultApplicationArguments()));
    }

    @Test
    void refusesCreateAndCreateDrop() {
        assertThrows(IllegalStateException.class,
                () -> new ProdSchemaGuard("create").run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class,
                () -> new ProdSchemaGuard("create-drop").run(new DefaultApplicationArguments()));
    }
}
