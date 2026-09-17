package com.sethdevkh.restapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdSchemaGuard implements ApplicationRunner {

    private final String ddlAuto;

    public ProdSchemaGuard(@Value("${spring.jpa.hibernate.ddl-auto:none}") String ddlAuto) {
        this.ddlAuto = ddlAuto;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("create".equalsIgnoreCase(ddlAuto) || "create-drop".equalsIgnoreCase(ddlAuto)) {
            throw new IllegalStateException("prod must not use spring.jpa.hibernate.ddl-auto=" + ddlAuto);
        }
    }
}
