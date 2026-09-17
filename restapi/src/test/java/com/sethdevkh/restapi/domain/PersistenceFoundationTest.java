package com.sethdevkh.restapi.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class PersistenceFoundationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private StandupRepository standupRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedCreatesOneLeadAndTwoMembersOnTheSameTeam() {
        var users = userRepository.findAll();
        assertEquals(3, users.size());

        User casey = userRepository.findByEmail("casey@demo.local").orElseThrow();
        User alex = userRepository.findByEmail("alex@demo.local").orElseThrow();
        User bailey = userRepository.findByEmail("bailey@demo.local").orElseThrow();

        assertEquals(Role.TEAM_LEAD, casey.getRole());
        assertEquals(Role.TEAM_MEMBER, alex.getRole());
        assertEquals(Role.TEAM_MEMBER, bailey.getRole());
        assertEquals(casey.getTeam().getId(), alex.getTeam().getId());
        assertEquals(casey.getTeam().getId(), bailey.getTeam().getId());
        assertEquals("Platform", casey.getTeam().getName());

        assertTrue(casey.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("test-only-lead-password", casey.getPasswordHash()));
        assertTrue(passwordEncoder.matches("test-only-alex-password", alex.getPasswordHash()));
        assertTrue(passwordEncoder.matches("test-only-bailey-password", bailey.getPasswordHash()));
    }

    @Test
    void teamScopedQueriesDoNotReturnOtherTeamsRows() {
        Team otherTeam = teamRepository.save(new Team("Other"));
        User otherUser = userRepository.save(new User(
                "other@demo.local",
                "Other Member",
                passwordEncoder.encode("not-a-demo-password"),
                Role.TEAM_MEMBER,
                otherTeam));

        User casey = userRepository.findByEmail("casey@demo.local").orElseThrow();
        User alex = userRepository.findByEmail("alex@demo.local").orElseThrow();
        Long platformId = TeamIds.of(casey);

        Task platformTask = taskRepository.save(Task.onCreatorTeam(
                "Platform task",
                null,
                TaskStatus.TO_DO,
                casey,
                alex,
                null));
        Task otherTask = taskRepository.save(Task.onCreatorTeam(
                "Other task",
                null,
                TaskStatus.TO_DO,
                otherUser,
                otherUser,
                null));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Standup platformStandup = standupRepository.save(Standup.onAuthorTeam(alex, today, "done", "", ""));
        Standup otherStandup = standupRepository.save(Standup.onAuthorTeam(otherUser, today, "other", "", ""));

        var platformTasks = taskRepository.findByTeamId(platformId);
        assertEquals(1, platformTasks.size());
        assertEquals(platformTask.getId(), platformTasks.getFirst().getId());
        assertTrue(taskRepository.findByIdAndTeamId(otherTask.getId(), platformId).isEmpty());

        var platformStandups = standupRepository.findByTeamIdAndStandupDate(platformId, today);
        assertEquals(1, platformStandups.size());
        assertEquals(platformStandup.getId(), platformStandups.getFirst().getId());
        assertTrue(standupRepository.findByIdAndTeamId(otherStandup.getId(), platformId).isEmpty());
    }

    @Test
    void taskFactoryRejectsOffTeamAssignee() {
        User casey = userRepository.findByEmail("casey@demo.local").orElseThrow();
        Team otherTeam = teamRepository.save(new Team("Outsiders"));
        User outsider = userRepository.save(new User(
                "outsider@demo.local",
                "Outsider",
                passwordEncoder.encode("not-a-demo-password"),
                Role.TEAM_MEMBER,
                otherTeam));

        assertThrows(IllegalArgumentException.class,
                () -> Task.onCreatorTeam("Nope", null, TaskStatus.TO_DO, casey, outsider, null));
    }

    @Test
    void standupDateIsUniquePerUser() {
        User alex = userRepository.findByEmail("alex@demo.local").orElseThrow();
        LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(3);
        standupRepository.saveAndFlush(Standup.onAuthorTeam(alex, day, "first", "", ""));

        assertThrows(DataIntegrityViolationException.class, () ->
                standupRepository.saveAndFlush(Standup.onAuthorTeam(alex, day, "duplicate", "", "")));
    }

    @Test
    void taskTeamComesFromCreatorNotAClientOverride() {
        User casey = userRepository.findByEmail("casey@demo.local").orElseThrow();
        User alex = userRepository.findByEmail("alex@demo.local").orElseThrow();
        Task task = Task.onCreatorTeam("Scoped", null, TaskStatus.TO_DO, casey, alex, null);
        assertEquals(TeamIds.of(casey), task.getTeam().getId());
        assertEquals(TeamIds.of(casey), TeamIds.of(alex));
    }

    @Test
    void committedProdConfigDoesNotCreateSchemaOrEmbedSecrets() throws Exception {
        String prod = Files.readString(Path.of("src/main/resources/application-prod.properties"));
        assertTrue(prod.contains("spring.jpa.hibernate.ddl-auto=none"));
        assertTrue(prod.contains("spring.datasource.password=${MYSQL_PASSWORD}"));
        assertTrue(prod.contains("spring.h2.console.enabled=false"));
        assertFalse(prod.contains("ddl-auto=create"));
        assertTrue(prod.contains("app.jwt.secret=${JWT_SECRET}"));
        assertTrue(prod.contains("app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}"));
        assertFalse(prod.contains("app.jwt.secret=test"));
        assertFalse(prod.contains("allowed-origins=*"));
    }
}
