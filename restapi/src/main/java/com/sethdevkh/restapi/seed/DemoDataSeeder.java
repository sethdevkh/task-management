package com.sethdevkh.restapi.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.Team;
import com.sethdevkh.restapi.domain.TeamRepository;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;

@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final SeedProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public DemoDataSeeder(
            SeedProperties properties,
            PasswordEncoder passwordEncoder,
            TeamRepository teamRepository,
            UserRepository userRepository) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (userRepository.count() > 0) {
            log.info("Skipping demo seed; users already exist");
            return;
        }
        if (!StringUtils.hasText(properties.teamName())) {
            throw new IllegalStateException("Demo seed is enabled but app.seed.team-name is empty");
        }
        requirePassword(properties.lead(), "DEMO_LEAD_PASSWORD");
        requirePassword(properties.alex(), "DEMO_MEMBER_ALEX_PASSWORD");
        requirePassword(properties.bailey(), "DEMO_MEMBER_BAILEY_PASSWORD");

        Team team = teamRepository.save(new Team(properties.teamName()));
        userRepository.save(user(properties.lead(), Role.TEAM_LEAD, team));
        userRepository.save(user(properties.alex(), Role.TEAM_MEMBER, team));
        userRepository.save(user(properties.bailey(), Role.TEAM_MEMBER, team));
        log.info("Seeded demo team '{}' with one lead and two members (demo-only)", team.getName());
    }

    private User user(SeedProperties.SeedUser spec, Role role, Team team) {
        return new User(
                spec.email(),
                spec.displayName(),
                passwordEncoder.encode(spec.password()),
                role,
                team);
    }

    private static void requirePassword(SeedProperties.SeedUser spec, String envName) {
        if (spec == null || !StringUtils.hasText(spec.password())) {
            throw new IllegalStateException(
                    "Demo seed is enabled but " + envName + " is empty. Set it from docs/operators.md; do not bake it into images.");
        }
    }
}
