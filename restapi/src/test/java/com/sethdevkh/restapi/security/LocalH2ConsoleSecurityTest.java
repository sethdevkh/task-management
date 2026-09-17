package com.sethdevkh.restapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalH2ConsoleSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void h2ConsoleIsNotUnauthorizedOnLocalProfile() throws Exception {
        mockMvc.perform(get("/h2-console/"))
                .andExpect(status().is(not(401)));
    }
}
