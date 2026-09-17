package com.sethdevkh.restapi.security;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sethdevkh.restapi.domain.Standup;
import com.sethdevkh.restapi.domain.StandupRepository;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Canonical PRD RBAC gate. CI fails this class if a member JWT receives 200 on
 * another member's task, another member's standup, lead aggregates, or illegal
 * assign/delete/list.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RbacSuiteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StandupRepository standupRepository;

    @Test
    void memberJwtIsPresentSoForbiddenIsNotAMissingToken401() {
        String alex = token("alex@demo.local");
        assertNotNull(alex);
        assertFalse(alex.isBlank());
    }

    @Test
    void memberCannotReadAnotherMembersTaskById() throws Exception {
        long baileyTask = createTask(token("bailey@demo.local"), """
                {"title":"Bailey secret task"}
                """);
        expectForbidden(
                "GET /api/tasks/{otherId}",
                mockMvc.perform(get("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberListDoesNotIncludeAnotherMembersTask() throws Exception {
        long baileyTask = createTask(token("bailey@demo.local"), """
                {"title":"Bailey list leak"}
                """);
        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) baileyTask))));
    }

    @Test
    void memberCannotReadAnotherMembersStandupById() throws Exception {
        long baileyStandup = upsert(token("bailey@demo.local"), """
                {"done":"Bailey secret standup"}
                """);
        expectForbidden(
                "GET /api/standups/{otherId}",
                mockMvc.perform(get("/api/standups/" + baileyStandup)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberHistoryDoesNotIncludeAnotherMembersStandup() throws Exception {
        upsert(token("bailey@demo.local"), """
                {"done":"Bailey history leak"}
                """);
        mockMvc.perform(get("/api/standups").header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].done", not(hasItem("Bailey history leak"))));
    }

    @Test
    void memberCannotReadLeadDashboardAggregates() throws Exception {
        expectForbidden(
                "GET /api/lead/dashboard",
                mockMvc.perform(get("/api/lead/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberCannotListLeadTeamTasks() throws Exception {
        expectForbidden(
                "GET /api/lead/tasks",
                mockMvc.perform(get("/api/lead/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberCannotAssignOrReassign() throws Exception {
        long caseyTask = createTask(token("casey@demo.local"), """
                {"title":"Lead owned for assign"}
                """);
        expectForbidden(
                "PATCH /api/lead/tasks/{id}/assignee",
                mockMvc.perform(patch("/api/lead/tasks/" + caseyTask + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """.formatted(userId("alex@demo.local")))));
        expectForbidden(
                "POST /api/tasks with another assignee",
                mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hand off","assigneeId":%d}
                                """.formatted(userId("bailey@demo.local")))));
    }

    @Test
    void memberCannotReadLeadStandupBoard() throws Exception {
        expectForbidden(
                "GET /api/lead/standups",
                mockMvc.perform(get("/api/lead/standups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberCannotEditOrDeleteAnotherPersonsTask() throws Exception {
        long baileyTask = createTask(token("bailey@demo.local"), """
                {"title":"Bailey keeps"}
                """);
        expectForbidden(
                "PATCH /api/tasks/{otherId}",
                mockMvc.perform(patch("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """)));
        expectForbidden(
                "DELETE /api/tasks/{otherId}",
                mockMvc.perform(delete("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberCannotDeleteTaskAssignedToSomeoneElse() throws Exception {
        long assignedOut = createTask(token("casey@demo.local"), """
                {"title":"Casey created for Alex","assigneeId":%d}
                """.formatted(userId("alex@demo.local")));
        expectForbidden(
                "DELETE assigned-to-member-but-created-by-lead",
                mockMvc.perform(delete("/api/tasks/" + assignedOut)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))));
    }

    @Test
    void memberCannotPatchAnotherMembersStandup() throws Exception {
        long baileyToday = upsert(token("bailey@demo.local"), """
                {"done":"Bailey today"}
                """);
        expectForbidden(
                "PATCH /api/standups/{otherId}",
                mockMvc.perform(patch("/api/standups/" + baileyToday)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Hijack"}
                                """)));
    }

    @Test
    void memberCannotEditPastStandup() throws Exception {
        User alex = user("alex@demo.local");
        Standup past = standupRepository.save(Standup.onAuthorTeam(
                alex,
                LocalDate.now(ZoneOffset.UTC).minusDays(1),
                "Old",
                "",
                ""));
        expectForbidden(
                "PATCH /api/standups/{pastId}",
                mockMvc.perform(patch("/api/standups/" + past.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alex@demo.local")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Rewrite history"}
                                """)));
    }

    @Test
    void prdForbiddenSurfaceNeverReturns200() throws Exception {
        long baileyTask = createTask(token("bailey@demo.local"), """
                {"title":"Suite probe task"}
                """);
        long baileyStandup = upsert(token("bailey@demo.local"), """
                {"done":"Suite probe standup"}
                """);
        String alex = bearer(token("alex@demo.local"));
        List<String> leaks = new ArrayList<>();
        record Call(String name, ResultActions actions) {
        }
        List<Call> calls = List.of(
                new Call("GET other task", mockMvc.perform(get("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, alex))),
                new Call("GET other standup", mockMvc.perform(get("/api/standups/" + baileyStandup)
                        .header(HttpHeaders.AUTHORIZATION, alex))),
                new Call("GET lead dashboard", mockMvc.perform(get("/api/lead/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, alex))),
                new Call("GET lead tasks", mockMvc.perform(get("/api/lead/tasks")
                        .header(HttpHeaders.AUTHORIZATION, alex))),
                new Call("GET lead standups", mockMvc.perform(get("/api/lead/standups")
                        .header(HttpHeaders.AUTHORIZATION, alex))),
                new Call("PATCH assign", mockMvc.perform(patch("/api/lead/tasks/" + baileyTask + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, alex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """.formatted(userId("alex@demo.local"))))),
                new Call("DELETE other task", mockMvc.perform(delete("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, alex))));
        for (Call call : calls) {
            int status = call.actions().andReturn().getResponse().getStatus();
            if (status == 200) {
                leaks.add(call.name() + " returned 200");
            }
        }
        if (!leaks.isEmpty()) {
            throw new AssertionError("RBAC suite leaked 200: " + String.join("; ", leaks));
        }
    }

    private void expectForbidden(String call, ResultActions actions) throws Exception {
        int status = actions.andReturn().getResponse().getStatus();
        assertNotEquals(200, status, () -> "RBAC leak: " + call + " returned 200");
        actions.andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Forbidden"));
    }

    private String token(String email) {
        return jwtService.createToken(user(email));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private long userId(String email) {
        return user(email).getId();
    }

    private long createTask(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return idFrom(body);
    }

    private long upsert(String token, String json) throws Exception {
        String body = mockMvc.perform(put("/api/standups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return idFrom(body);
    }

    private static long idFrom(String body) {
        int start = body.indexOf("\"id\":") + 5;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }
}
