package com.sethdevkh.restapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.AuthenticatedUser;
import com.sethdevkh.restapi.security.JwtService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void loginReturnsJwtRoleAndDisplayName() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"casey@demo.local","password":"test-only-lead-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("TEAM_LEAD"))
                .andExpect(jsonPath("$.displayName").value("Casey Lead"))
                .andExpect(jsonPath("$.team_id").doesNotExist())
                .andExpect(jsonPath("$.teamId").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = tokenFrom(body);
        AuthenticatedUser principal = jwtService.parse(token).orElseThrow();
        User casey = userRepository.findByEmail("casey@demo.local").orElseThrow();
        assertEquals(casey.getId(), principal.userId());
        assertEquals("casey@demo.local", principal.email());
        assertEquals(Role.TEAM_LEAD, principal.role());
        assertEquals("Casey Lead", principal.displayName());
    }

    @Test
    void loginIgnoresClientTeamId() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"alex@demo.local",
                                  "password":"test-only-alex-password",
                                  "team_id": 999,
                                  "role": "TEAM_LEAD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Alex Member"))
                .andExpect(jsonPath("$.team_id").doesNotExist());
    }

    @Test
    void unknownUserAndWrongPasswordReturnTheSameUnauthorizedBody() throws Exception {
        String unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@demo.local","password":"test-only-lead-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String wrong = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"casey@demo.local","password":"not-the-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(unknown, wrong);
        assertFalse(unknown.toLowerCase().contains("not found"));
        assertFalse(unknown.toLowerCase().contains("password"));
        assertFalse(unknown.toLowerCase().contains("casey"));
    }

    @Test
    void loginDoesNotCreateUsers() throws Exception {
        long before = userRepository.count();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@demo.local","password":"anything"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@demo.local","password":"anything"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
        assertEquals(before, userRepository.count());
        assertTrue(userRepository.findByEmail("new@demo.local").isEmpty());
    }

    @Test
    void blankLoginIsBadRequestNotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    private static String tokenFrom(String body) {
        int start = body.indexOf("\"token\":\"") + 9;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
