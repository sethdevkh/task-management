package com.sethdevkh.restapi.mvp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sethdevkh.restapi.domain.Standup;
import com.sethdevkh.restapi.domain.StandupRepository;
import com.sethdevkh.restapi.domain.Task;
import com.sethdevkh.restapi.domain.TaskRepository;
import com.sethdevkh.restapi.domain.TaskStatus;
import com.sethdevkh.restapi.domain.TeamIds;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production-like MVP walkthrough with Spring Security enabled. Seeded login
 * mints a real JWT; Security is not disabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MvpWalkthroughTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private StandupRepository standupRepository;

    @Test
    void seededLeadCanAnswerOverloadAndMissingStandupsWithoutAnotherEndpoint() throws Exception {
        String casey = login("casey@demo.local", "test-only-lead-password");
        String alex = login("alex@demo.local", "test-only-alex-password");
        long baileyId = userId("bailey@demo.local");

        createTask(casey, """
                {"title":"Alex load 1","assigneeId":%d}
                """.formatted(userId("alex@demo.local")));
        createTask(casey, """
                {"title":"Alex load 2","assigneeId":%d}
                """.formatted(userId("alex@demo.local")));
        createTask(casey, """
                {"title":"Bailey one","assigneeId":%d}
                """.formatted(baileyId));
        upsert(alex, """
                {"done":"Walkthrough standup"}
                """);

        DocumentContext dashboard = getDashboard(casey);
        Map<String, Object> overloaded = workloadRows(dashboard).stream()
                .max(Comparator.comparingLong(row -> asLong(row.get("toDo")) + asLong(row.get("inProgress"))))
                .orElseThrow();
        assertEquals("Alex Member", overloaded.get("displayName"));
        assertTrue(asLong(overloaded.get("toDo")) + asLong(overloaded.get("inProgress")) > 1);

        Set<String> missing = names(dashboard, "$.standupPresence.missing[*].displayName");
        Set<String> submitted = names(dashboard, "$.standupPresence.submitted[*].displayName");
        assertTrue(submitted.contains("Alex Member"));
        assertTrue(missing.contains("Bailey Member"));
        assertFalse(submitted.contains("Bailey Member"));
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), dashboard.read("$.standupPresence.date"));
        assertDashboardMatchesDatabase(dashboard, user("casey@demo.local"));
    }

    @Test
    void seededMemberCanCreateTaskAndSubmitTodayStandupInOneSitting() throws Exception {
        String alex = login("alex@demo.local", "test-only-alex-password");

        long taskId = createTask(alex, """
                {"title":"Walkthrough member task","description":"Created in one sitting"}
                """);
        mockMvc.perform(get("/api/tasks").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) taskId)))
                .andExpect(jsonPath("$[*].title", hasItem("Walkthrough member task")));

        long standupId = upsert(alex, """
                {"done":"Created the walkthrough task","doing":"Submitting standup","blockers":""}
                """);
        mockMvc.perform(get("/api/standups").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) standupId)))
                .andExpect(jsonPath("$[0].standupDate").value(LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$[0].done").value("Created the walkthrough task"));
        mockMvc.perform(get("/api/standups/" + standupId).header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true));
    }

    @Test
    void dashboardTotalsMatchDatabaseAfterStatusChangeAndStandupSubmit() throws Exception {
        String casey = login("casey@demo.local", "test-only-lead-password");
        String alex = login("alex@demo.local", "test-only-alex-password");
        String bailey = login("bailey@demo.local", "test-only-bailey-password");
        User lead = user("casey@demo.local");

        long alexTask = createTask(alex, """
                {"title":"Freshness todo"}
                """);
        DocumentContext before = getDashboard(casey);
        assertDashboardMatchesDatabase(before, lead);
        long alexToDoBefore = asLong(workloadRow(before, "Alex Member").get("toDo"));
        long alexDoingBefore = asLong(workloadRow(before, "Alex Member").get("inProgress"));
        long mixToDoBefore = asLong(before.read("$.statusMix.TO_DO"));
        long mixDoingBefore = asLong(before.read("$.statusMix.IN_PROGRESS"));

        mockMvc.perform(patch("/api/tasks/" + alexTask)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk());

        DocumentContext afterStatus = getDashboard(casey);
        assertDashboardMatchesDatabase(afterStatus, lead);
        assertEquals(alexToDoBefore - 1, asLong(workloadRow(afterStatus, "Alex Member").get("toDo")));
        assertEquals(alexDoingBefore + 1, asLong(workloadRow(afterStatus, "Alex Member").get("inProgress")));
        assertEquals(mixToDoBefore - 1, asLong(afterStatus.read("$.statusMix.TO_DO")));
        assertEquals(mixDoingBefore + 1, asLong(afterStatus.read("$.statusMix.IN_PROGRESS")));

        upsert(bailey, """
                {"doing":"Freshness standup"}
                """);
        DocumentContext afterStandup = getDashboard(casey);
        assertDashboardMatchesDatabase(afterStandup, lead);
        assertTrue(names(afterStandup, "$.standupPresence.submitted[*].displayName").contains("Bailey Member"));
    }

    private void assertDashboardMatchesDatabase(DocumentContext dashboard, User lead) {
        Long teamId = TeamIds.of(lead);
        List<User> teamUsers = userRepository.findByTeamId(teamId);
        List<Task> tasks = taskRepository.findOnTeamFiltered(teamId, null, null);
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        Map<Long, long[]> active = new HashMap<>();
        for (User user : teamUsers) {
            active.put(user.getId(), new long[] {0, 0});
        }
        long toDo = 0;
        long inProgress = 0;
        long completed = 0;
        long completedInWindow = 0;
        for (Task task : tasks) {
            if (task.getStatus() == TaskStatus.TO_DO) {
                toDo++;
                active.get(task.getAssignee().getId())[0]++;
            } else if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                inProgress++;
                active.get(task.getAssignee().getId())[1]++;
            } else if (task.getStatus() == TaskStatus.COMPLETED) {
                completed++;
            }
            if (task.getStatus() == TaskStatus.COMPLETED) {
                Instant when = task.getCompletedAt() != null ? task.getCompletedAt() : task.getUpdatedAt();
                if (!when.isBefore(cutoff)) {
                    completedInWindow++;
                }
            }
        }

        List<Map<String, Object>> workload = workloadRows(dashboard);
        assertEquals(teamUsers.size(), workload.size());
        for (Map<String, Object> row : workload) {
            long[] counts = active.get(asLong(row.get("id")));
            assertEquals(counts[0], asLong(row.get("toDo")), row.get("displayName") + " toDo");
            assertEquals(counts[1], asLong(row.get("inProgress")), row.get("displayName") + " inProgress");
        }
        assertEquals(toDo, asLong(dashboard.read("$.statusMix.TO_DO")));
        assertEquals(inProgress, asLong(dashboard.read("$.statusMix.IN_PROGRESS")));
        assertEquals(completed, asLong(dashboard.read("$.statusMix.COMPLETED")));
        assertEquals(tasks.size(), asLong(dashboard.read("$.completion.totalTasks")));
        assertEquals(completedInWindow, asLong(dashboard.read("$.completion.completedInLast7Days")));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Set<Long> submittedIds = standupRepository.findByTeamIdAndStandupDateWithUser(teamId, today).stream()
                .map(Standup::getUser)
                .map(User::getId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> dashboardSubmitted = ids(dashboard, "$.standupPresence.submitted[*].id");
        Set<Long> dashboardMissing = ids(dashboard, "$.standupPresence.missing[*].id");
        Set<Long> expectedMissing = teamUsers.stream()
                .map(User::getId)
                .filter(id -> !submittedIds.contains(id))
                .collect(Collectors.toSet());
        assertEquals(today.toString(), dashboard.read("$.standupPresence.date"));
        assertEquals(submittedIds, dashboardSubmitted);
        assertEquals(expectedMissing, dashboardMissing);
    }

    private DocumentContext getDashboard(String token) throws Exception {
        String body = mockMvc.perform(get("/api/lead/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(body);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> workloadRows(DocumentContext dashboard) {
        return dashboard.read("$.workload");
    }

    private static Map<String, Object> workloadRow(DocumentContext dashboard, String name) {
        return workloadRows(dashboard).stream()
                .filter(row -> name.equals(row.get("displayName")))
                .findFirst()
                .orElseThrow();
    }

    private static Set<String> names(DocumentContext dashboard, String path) {
        List<String> values = dashboard.read(path);
        return new HashSet<>(values);
    }

    private static Set<Long> ids(DocumentContext dashboard, String path) {
        List<Number> values = dashboard.read(path);
        return values.stream().map(Number::longValue).collect(Collectors.toSet());
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = body.indexOf("\"token\":\"") + 9;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
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
