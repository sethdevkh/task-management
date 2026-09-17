package com.sethdevkh.restapi.standup;

import java.time.LocalDate;
import java.time.ZoneOffset;

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
import com.sethdevkh.restapi.domain.Team;
import com.sethdevkh.restapi.domain.TeamRepository;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.JwtService;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StandupApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private StandupRepository standupRepository;

    @Test
    void upsertCreatesTodayThenUpdatesSameRowAndIgnoresClientTeam() throws Exception {
        String alex = token("alex@demo.local");
        long alexId = userId("alex@demo.local");
        String today = todayUtc();

        String createdBody = mockMvc.perform(put("/api/standups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "done":"Shipped login",
                                  "doing":"Standup API",
                                  "blockers":"",
                                  "team_id": 999,
                                  "teamId": 999,
                                  "userId": 1,
                                  "createdAt": "2000-01-01T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value((int) alexId))
                .andExpect(jsonPath("$.displayName").value("Alex Member"))
                .andExpect(jsonPath("$.standupDate").value(today))
                .andExpect(jsonPath("$.done").value("Shipped login"))
                .andExpect(jsonPath("$.doing").value("Standup API"))
                .andExpect(jsonPath("$.blockers").value(""))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.teamId").doesNotExist())
                .andExpect(jsonPath("$.team_id").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = idFrom(createdBody);

        mockMvc.perform(put("/api/standups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Updated done","doing":"Still standup","blockers":"None"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.done").value("Updated done"))
                .andExpect(jsonPath("$.doing").value("Still standup"))
                .andExpect(jsonPath("$.blockers").value("None"))
                .andExpect(jsonPath("$.standupDate").value(today));

        assertEquals(1, standupRepository.findOwnOnTeam(alexId, userRepository.findByEmail("alex@demo.local")
                .orElseThrow()
                .getTeam()
                .getId()).size());
    }

    @Test
    void blankUpsertIsBadRequest() throws Exception {
        String alex = token("alex@demo.local");
        mockMvc.perform(put("/api/standups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"  ","doing":"","blockers":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At least one of Done, Doing, or Blockers is required"));
    }

    @Test
    void upsertOfPastDayIsForbidden() throws Exception {
        String alex = token("alex@demo.local");
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        mockMvc.perform(put("/api/standups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Backdate","standupDate":"%s"}
                                """.formatted(yesterday)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void memberHistoryIsOwnOnlyAndTodayIsEditable() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        User alexUser = user("alex@demo.local");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        standupRepository.save(Standup.onAuthorTeam(alexUser, yesterday, "Yesterday done", "", ""));
        long todayId = upsert(alex, """
                {"doing":"Today work"}
                """);
        upsert(bailey, """
                {"done":"Bailey today"}
                """);

        mockMvc.perform(get("/api/standups").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) todayId)))
                .andExpect(jsonPath("$[*].done", not(hasItem("Bailey today"))))
                .andExpect(jsonPath("$[0].standupDate").value(today.toString()))
                .andExpect(jsonPath("$[0].editable").value(true))
                .andExpect(jsonPath("$[1].standupDate").value(yesterday.toString()))
                .andExpect(jsonPath("$[1].editable").value(false))
                .andExpect(jsonPath("$[0].teamId").doesNotExist());
    }

    @Test
    void memberGetOfAnotherMembersStandupIsForbidden() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        long baileyToday = upsert(bailey, """
                {"done":"Bailey secret"}
                """);

        mockMvc.perform(get("/api/standups/" + baileyToday).header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void leadCanGetAnotherMembersStandupById() throws Exception {
        String bailey = token("bailey@demo.local");
        String casey = token("casey@demo.local");
        long baileyToday = upsert(bailey, """
                {"done":"Bailey secret"}
                """);

        mockMvc.perform(get("/api/standups/" + baileyToday).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value("Bailey secret"))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    void patchOfPastDayIsForbiddenAndTodayPatchUpdates() throws Exception {
        String alex = token("alex@demo.local");
        User alexUser = user("alex@demo.local");
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Standup past = standupRepository.save(Standup.onAuthorTeam(alexUser, yesterday, "Old", "", ""));
        long todayId = upsert(alex, """
                {"done":"Today"}
                """);

        mockMvc.perform(patch("/api/standups/" + past.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Rewrite history"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/standups/" + todayId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blockers":"Waiting on review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) todayId))
                .andExpect(jsonPath("$.done").value("Today"))
                .andExpect(jsonPath("$.blockers").value("Waiting on review"));
    }

    @Test
    void memberCannotPatchAnotherMembersTodayStandup() throws Exception {
        String alex = token("alex@demo.local");
        String bailey = token("bailey@demo.local");
        long baileyToday = upsert(bailey, """
                {"done":"Bailey today"}
                """);

        mockMvc.perform(patch("/api/standups/" + baileyToday)
                        .header(HttpHeaders.AUTHORIZATION, bearer(alex))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Hijack"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void leadBoardListsSubmittedAndMissingForSelectedDate() throws Exception {
        String alex = token("alex@demo.local");
        String casey = token("casey@demo.local");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);
        standupRepository.save(Standup.onAuthorTeam(user("bailey@demo.local"), yesterday, "Bailey yesterday", "", ""));
        upsert(alex, """
                {"done":"Alex today"}
                """);

        mockMvc.perform(get("/api/lead/standups").header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.submitted", hasSize(1)))
                .andExpect(jsonPath("$.submitted[0].displayName").value("Alex Member"))
                .andExpect(jsonPath("$.submitted[0].done").value("Alex today"))
                .andExpect(jsonPath("$.missing[*].displayName", containsInAnyOrder("Bailey Member", "Casey Lead")))
                .andExpect(jsonPath("$.teamId").doesNotExist());

        mockMvc.perform(get("/api/lead/standups")
                        .param("date", yesterday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(yesterday.toString()))
                .andExpect(jsonPath("$.submitted", hasSize(1)))
                .andExpect(jsonPath("$.submitted[0].displayName").value("Bailey Member"))
                .andExpect(jsonPath("$.missing[*].displayName", containsInAnyOrder("Alex Member", "Casey Lead")));
    }

    @Test
    void memberHittingLeadStandupBoardIsForbidden() throws Exception {
        String alex = token("alex@demo.local");
        assertNotNull(alex);
        assertFalse(alex.isBlank());
        mockMvc.perform(get("/api/lead/standups").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void missingTokenOnStandupRoutesIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/standups")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lead/standups")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/standups").contentType(MediaType.APPLICATION_JSON).content("""
                {"done":"Nope"}
                """)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownStandupOnOwnTeamIsNotFoundAndOffTeamDoesNotLeak() throws Exception {
        String alex = token("alex@demo.local");
        String casey = token("casey@demo.local");
        User outsider = outsider();
        Standup outsiderStandup = standupRepository.save(Standup.onAuthorTeam(
                outsider,
                LocalDate.now(ZoneOffset.UTC),
                "Other team",
                "",
                ""));

        mockMvc.perform(get("/api/standups/999999").header(HttpHeaders.AUTHORIZATION, bearer(alex)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/standups/" + outsiderStandup.getId()).header(HttpHeaders.AUTHORIZATION, bearer(casey)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/standups/" + outsiderStandup.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(casey))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done":"Nope"}
                                """))
                .andExpect(status().isNotFound());
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

    private static String todayUtc() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private long upsert(String token, String json) throws Exception {
        String body = mockMvc.perform(put("/api/standups")
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
}
