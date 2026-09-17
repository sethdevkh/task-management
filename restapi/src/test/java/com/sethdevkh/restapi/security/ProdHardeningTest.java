package com.sethdevkh.restapi.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProdHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void onlyHealthIsUnauthenticatedApiGet() throws Exception {
        Set<String> getPaths = new LinkedHashSet<>();
        requestMappingHandlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (!mapsGet(info)) {
                return;
            }
            for (String pattern : pathPatterns(info)) {
                if (pattern.startsWith("/api/")) {
                    getPaths.add(pattern);
                }
            }
        });
        assertTrue(getPaths.contains("/api/health"));
        assertFalse(getPaths.contains("/api/auth/login"));

        for (String pattern : getPaths) {
            String path = pattern.replace("{id}", "1");
            int status = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            if ("/api/health".equals(pattern)) {
                assertEquals(200, status, path + " must stay public");
            } else {
                assertEquals(401, status, () -> "unauthenticated GET " + path + " must not be public, was " + status);
            }
        }

        mockMvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPostStaysTheOtherPublicPathButIsNotAGet() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"missing@demo.local","password":"nope"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void healthResponseHasSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void httpsHealthIncludesHsts() throws Exception {
        mockMvc.perform(get("/api/health").secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=")));
    }

    @Test
    void protectedGetDoesNotUseWildcardCors() throws Exception {
        var response = mockMvc.perform(get("/api/tasks").header("Origin", "https://evil.example"))
                .andReturn()
                .getResponse();
        int status = response.getStatus();
        assertTrue(status == 401 || status == 403, () -> "rejected origin must not read the resource, was " + status);
        assertEquals(null, response.getHeader("Access-Control-Allow-Origin"));
    }

    private static boolean mapsGet(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() || methods.contains(RequestMethod.GET);
    }

    private static Set<String> pathPatterns(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns();
        }
        return Set.of();
    }
}
