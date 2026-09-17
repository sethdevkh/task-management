package com.sethdevkh.restapi.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.Team;
import com.sethdevkh.restapi.domain.TeamRepository;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.JwtService;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Test
    void memberListsOnlyCreatedOrAssignedTasksOnOwnTeam() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        String casey = token("casey@demo.local");

        long alexOwn = createTask(alex, """
                {"title":"Alex own"}
                """);
        long caseyToAlex = createTask(casey, """
                {"title":"Casey to Alex","assigneeId":%d}
                """.formatted(userId("alex@demo.local")));
        long baileyOwn = createTask(bailey, """
                {"title":"Bailey own"}
                """);

        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) alexOwn)))
                .andExpect(jsonPath("$[*].id", hasItem((int) caseyToAlex)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) baileyOwn))))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Alex own", "Casey to Alex")));
    }

    @Test
    void memberGetByIdOfAnotherMembersTaskIsForbidden() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        long baileyTask = createTask(bailey, """
                {"title":"Bailey secret"}
                """);

        mockMvc.perform(get("/api/tasks/" + baileyTask).header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void leadCanGetAnotherMembersTaskById() throws Exception {
        String bailey = token("bailey@demo.local");
        String casey = token("casey@demo.local");
        long baileyTask = createTask(bailey, """
                {"title":"Bailey secret"}
                """);

        mockMvc.perform(get("/api/tasks/" + baileyTask).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Bailey secret"));
    }

    @Test
    void missingTokenOnTaskRoutesIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lead/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Nope"}
                """)).andExpect(status().isUnauthorized());
    }

    @Test
    void memberCreateDefaultsAssigneeToSelfAndIgnoresClientTeamAndCreator() throws Exception {
        String alex = token("alex@demo.local");
        long alexId = userId("alex@demo.local");

        mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Self assigned",
                                  "team_id": 999,
                                  "teamId": 999,
                                  "creatorId": 1,
                                  "createdAt": "2000-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Self assigned"))
                .andExpect(jsonPath("$.status").value("TO_DO"))
                .andExpect(jsonPath("$.assigneeId").value((int) alexId))
                .andExpect(jsonPath("$.creatorId").value((int) alexId))
                .andExpect(jsonPath("$.assigneeDisplayName").value("Alex Member"))
                .andExpect(jsonPath("$.team_id").doesNotExist())
                .andExpect(jsonPath("$.teamId").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void memberCannotAssignToAnotherPersonOnCreate() throws Exception {
        String alex = token("alex@demo.local");
        long baileyId = userId("bailey@demo.local");

        mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hand off","assigneeId":%d}
                                """.formatted(baileyId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadCanAssignTeammateOnCreateAndOffTeamAssigneeIsBadRequest() throws Exception {
        String casey = token("casey@demo.local");
        long baileyId = userId("bailey@demo.local");
        long outsiderId = outsiderId();

        mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"For Bailey","assigneeId":%d}
                                """.formatted(baileyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeId").value((int) baileyId))
                .andExpect(jsonPath("$.creatorId").value((int) userId("casey@demo.local")));

        mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Off team","assigneeId":%d}
                                """.formatted(outsiderId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Assignee is not on the team"));
    }

    @Test
    void blankTitleOnCreateIsBadRequest() throws Exception {
        String alex = token("alex@demo.local");
        mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberCanUpdateOwnOrAssignedAndLeadCanUpdateAnyTeamTask() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        String casey = token("casey@demo.local");
        long alexTask = createTask(alex, """
                {"title":"Alex edits"}
                """);
        long baileyTask = createTask(bailey, """
                {"title":"Bailey task"}
                """);
        long caseyToAlex = createTask(casey, """
                {"title":"Assigned to Alex","assigneeId":%d}
                """.formatted(userId("alex@demo.local")));

        mockMvc.perform(patch("/api/tasks/" + alexTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/tasks/" + caseyToAlex)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Alex renamed assigned"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alex renamed assigned"));

        mockMvc.perform(patch("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isString());
    }

    @Test
    void memberUpdateOfAnotherMembersTaskIsForbiddenAndBlankTitleIsBadRequest() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        long baileyTask = createTask(bailey, """
                {"title":"Hands off"}
                """);
        long alexTask = createTask(alex, """
                {"title":"Keep me"}
                """);

        mockMvc.perform(patch("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/tasks/" + alexTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Title is required"));
    }

    @Test
    void leadListsAllTeamTasksAndFiltersByMemberAndStatus() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        String casey = token("casey@demo.local");
        long alexId = userId("alex@demo.local");
        long alexTask = createTask(alex, """
                {"title":"Alex todo"}
                """);
        long baileyTask = createTask(bailey, """
                {"title":"Bailey doing"}
                """);
        mockMvc.perform(patch("/api/tasks/" + baileyTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bailey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/lead/tasks").header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[*].id", hasItem((int) alexTask)))
                .andExpect(jsonPath("$.tasks[*].id", hasItem((int) baileyTask)))
                .andExpect(jsonPath("$.members[*].displayName", hasItem("Alex Member")))
                .andExpect(jsonPath("$.members[*].displayName", hasItem("Casey Lead")));

        mockMvc.perform(get("/api/lead/tasks")
                        .param("assigneeId", String.valueOf(alexId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[*].id", hasItem((int) alexTask)))
                .andExpect(jsonPath("$.tasks[*].id", not(hasItem((int) baileyTask))));

        mockMvc.perform(get("/api/lead/tasks")
                        .param("status", "IN_PROGRESS")
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[*].id", hasItem((int) baileyTask)))
                .andExpect(jsonPath("$.tasks[*].id", not(hasItem((int) alexTask))));
    }

    @Test
    void memberHittingLeadListOrAssignIsForbidden() throws Exception {
        String alex = token("alex@demo.local");
        String casey = token("casey@demo.local");
        long caseyTask = createTask(casey, """
                {"title":"Lead owned"}
                """);

        mockMvc.perform(get("/api/lead/tasks").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/lead/tasks/" + caseyTask + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """.formatted(userId("alex@demo.local"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadCanReassignIncludingSelfAndOffTeamAssigneeIsBadRequest() throws Exception {
        String casey = token("casey@demo.local");
        long caseyId = userId("casey@demo.local");
        long baileyId = userId("bailey@demo.local");
        long taskId = createTask(casey, """
                {"title":"Reassign me","assigneeId":%d}
                """.formatted(baileyId));

        mockMvc.perform(patch("/api/lead/tasks/" + taskId + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """.formatted(caseyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value((int) caseyId));

        mockMvc.perform(patch("/api/lead/tasks/" + taskId + "/assignee")
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assigneeId":%d}
                                """.formatted(outsiderId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Assignee is not on the team"));
    }

    @Test
    void deleteRulesMatchPrd() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        String casey = token("casey@demo.local");
        long alexOwn = createTask(alex, """
                {"title":"Alex can delete"}
                """);
        long alexAssignedOut = createTask(casey, """
                {"title":"Casey created for Alex","assigneeId":%d}
                """.formatted(userId("alex@demo.local")));
        long baileyOwn = createTask(bailey, """
                {"title":"Bailey keeps"}
                """);
        long leadDeletes = createTask(bailey, """
                {"title":"Lead deletes Bailey"}
                """);

        mockMvc.perform(delete("/api/tasks/" + alexOwn).header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/tasks/" + alexAssignedOut).header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tasks/" + baileyOwn).header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tasks/" + leadDeletes).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tasks/" + leadDeletes).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownTaskOnOwnTeamIsNotFoundAndOffTeamTaskDoesNotLeak() throws Exception {
        String alex = token("alex@demo.local");
        String casey = token("casey@demo.local");
        User outsider = outsider();
        String outsiderToken = jwtService.createToken(outsider);
        long outsiderTask = createTask(outsiderToken, """
                {"title":"Other team"}
                """);

        mockMvc.perform(get("/api/tasks/999999").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/" + outsiderTask).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tasks/" + outsiderTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/" + outsiderTask).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberJwtNotMissingTokenIsUsedForForbiddenLeadList() throws Exception {
        String alex = token("alex@demo.local");
        assertNotNull(alex);
        assertFalse(alex.isBlank());
        mockMvc.perform(get("/api/lead/tasks").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    private String token(String email) {
        return jwtService.createToken(userRepository.findByEmail(email).orElseThrow());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private long createTask(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").doesNotExist())
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

    private User outsider() {
        return userRepository.findByEmail("outsider@demo.local").orElseGet(() -> {
            Team team = teamRepository.save(new Team("Outsiders"));
            return userRepository.save(new User(
                    "outsider@demo.local",
                    "Outsider",
                    "$2a$10$notusedinthisapitestxxxxxxxxxxxxxxxxxxxxxxx",
                    Role.TEAM_MEMBER,
                    team));
        });
    }

    private long outsiderId() {
        return outsider().getId();
    }
}
