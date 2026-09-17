package com.sethdevkh.restapi.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.Standup;
import com.sethdevkh.restapi.domain.StandupRepository;
import com.sethdevkh.restapi.domain.Task;
import com.sethdevkh.restapi.domain.TaskRepository;
import com.sethdevkh.restapi.domain.TaskStatus;
import com.sethdevkh.restapi.domain.Team;
import com.sethdevkh.restapi.domain.TeamRepository;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.JwtService;

import jakarta.persistence.EntityManager;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private StandupRepository standupRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void leadDashboardAggregatesWorkloadMixCompletionAndTodayPresence() throws Exception {
        String casey = token("casey@demo.local");
        String alex = token("alex@demo.local");
        long alexId = userId("alex@demo.local");
        long baileyId = userId("bailey@demo.local");
        long caseyId = userId("casey@demo.local");

        createTask(alex, """
                {"title":"Alex todo"}
                """);
        long alexDoing = createTask(alex, """
                {"title":"Alex doing"}
                """);
        mockMvc.perform(patch("/api/tasks/" + alexDoing)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());
        long alexDone = createTask(alex, """
                {"title":"Alex done"}
                """);
        mockMvc.perform(patch("/api/tasks/" + alexDone)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk());

        createTask(casey, """
                {"title":"Bailey todo","assigneeId":%d}
                """.formatted(baileyId));

        Task oldDone = taskRepository.save(Task.onCreatorTeam(
                "Old completion",
                null,
                TaskStatus.COMPLETED,
                user("casey@demo.local"),
                user("bailey@demo.local"),
                null));
        entityManager.flush();
        entityManager.createNativeQuery("update tasks set completed_at = :when where id = :id")
                .setParameter("when", Instant.now().minus(8, ChronoUnit.DAYS))
                .setParameter("id", oldDone.getId())
                .executeUpdate();
        entityManager.clear();

        standupRepository.save(Standup.onAuthorTeam(
                user("alex@demo.local"),
                LocalDate.now(ZoneOffset.UTC),
                "Alex today",
                "",
                ""));

        User outsiderLead = outsiderLead();
        taskRepository.save(Task.onCreatorTeam(
                "Other team task",
                null,
                TaskStatus.TO_DO,
                outsiderLead,
                outsiderLead,
                null));
        standupRepository.save(Standup.onAuthorTeam(
                outsiderLead,
                LocalDate.now(ZoneOffset.UTC),
                "Other team standup",
                "",
                ""));

        mockMvc.perform(get("/api/lead/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").doesNotExist())
                .andExpect(jsonPath("$.team_id").doesNotExist())
                .andExpect(jsonPath("$.workload", hasSize(3)))
                .andExpect(jsonPath("$.workload[*].displayName",
                        contains("Alex Member", "Bailey Member", "Casey Lead")))
                .andExpect(jsonPath("$.workload[0].id").value((int) alexId))
                .andExpect(jsonPath("$.workload[0].toDo").value(1))
                .andExpect(jsonPath("$.workload[0].inProgress").value(1))
                .andExpect(jsonPath("$.workload[1].id").value((int) baileyId))
                .andExpect(jsonPath("$.workload[1].toDo").value(1))
                .andExpect(jsonPath("$.workload[1].inProgress").value(0))
                .andExpect(jsonPath("$.workload[2].id").value((int) caseyId))
                .andExpect(jsonPath("$.workload[2].toDo").value(0))
                .andExpect(jsonPath("$.workload[2].inProgress").value(0))
                .andExpect(jsonPath("$.statusMix.TO_DO").value(2))
                .andExpect(jsonPath("$.statusMix.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.statusMix.COMPLETED").value(2))
                .andExpect(jsonPath("$.completion.completedInLast7Days").value(1))
                .andExpect(jsonPath("$.completion.totalTasks").value(5))
                .andExpect(jsonPath("$.completion.rate").value(closeTo(0.2, 0.0001)))
                .andExpect(jsonPath("$.standupPresence.date").value(LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.standupPresence.submitted", hasSize(1)))
                .andExpect(jsonPath("$.standupPresence.submitted[0].displayName").value("Alex Member"))
                .andExpect(jsonPath("$.standupPresence.missing[*].displayName",
                        containsInAnyOrder("Bailey Member", "Casey Lead")));
    }

    @Test
    void emptyTeamRendersZerosAndEmptyStandupLists() throws Exception {
        User solo = soloLead();
        String token = jwtService.createToken(solo);

        mockMvc.perform(get("/api/lead/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workload", hasSize(1)))
                .andExpect(jsonPath("$.workload[0].displayName").value("Solo Lead"))
                .andExpect(jsonPath("$.workload[0].toDo").value(0))
                .andExpect(jsonPath("$.workload[0].inProgress").value(0))
                .andExpect(jsonPath("$.statusMix.TO_DO").value(0))
                .andExpect(jsonPath("$.statusMix.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.statusMix.COMPLETED").value(0))
                .andExpect(jsonPath("$.completion.rate").value(0.0))
                .andExpect(jsonPath("$.completion.completedInLast7Days").value(0))
                .andExpect(jsonPath("$.completion.totalTasks").value(0))
                .andExpect(jsonPath("$.standupPresence.submitted", hasSize(0)))
                .andExpect(jsonPath("$.standupPresence.missing", hasSize(1)))
                .andExpect(jsonPath("$.standupPresence.missing[0].displayName").value("Solo Lead"));
    }

    @Test
    void memberJwtCallingDashboardIsForbidden() throws Exception {
        String alex = token("alex@demo.local");
        assertNotNull(alex);
        assertFalse(alex.isBlank());
        mockMvc.perform(get("/api/lead/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void missingTokenOnDashboardIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/lead/dashboard")).andExpect(status().isUnauthorized());
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

    private static long idFrom(String body) {
        int start = body.indexOf("\"id\":") + 5;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.parseLong(body.substring(start, end));
    }

    private User outsiderLead() {
        return userRepository.findByEmail("outsider-lead@demo.local").orElseGet(() -> {
            Team team = teamRepository.save(new Team("Outsiders"));
            return userRepository.save(new User(
                    "outsider-lead@demo.local",
                    "Outsider Lead",
                    "$2a$10$notusedinthisapitestxxxxxxxxxxxxxxxxxxxxxxx",
                    Role.TEAM_LEAD,
                    team));
        });
    }

    private User soloLead() {
        return userRepository.findByEmail("solo-lead@demo.local").orElseGet(() -> {
            Team team = teamRepository.save(new Team("Solo"));
            return userRepository.save(new User(
                    "solo-lead@demo.local",
                    "Solo Lead",
                    "$2a$10$notusedinthisapitestxxxxxxxxxxxxxxxxxxxxxxx",
                    Role.TEAM_LEAD,
                    team));
        });
    }
}
