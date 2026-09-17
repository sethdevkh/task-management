package com.sethdevkh.restapi.standup;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.Standup;
import com.sethdevkh.restapi.domain.StandupRepository;
import com.sethdevkh.restapi.domain.TeamIds;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.CurrentUserService;
import com.sethdevkh.restapi.web.BadRequestException;
import com.sethdevkh.restapi.web.ForbiddenException;
import com.sethdevkh.restapi.web.NotFoundException;

@Service
public class StandupService {

    static final String AT_LEAST_ONE_FIELD = "At least one of Done, Doing, or Blockers is required";

    private final CurrentUserService currentUserService;
    private final StandupRepository standupRepository;
    private final UserRepository userRepository;

    public StandupService(
            CurrentUserService currentUserService,
            StandupRepository standupRepository,
            UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.standupRepository = standupRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public StandupWriteResult upsertToday(UpsertStandupRequest request) {
        User actor = currentUserService.requireUser();
        LocalDate today = todayUtc();
        rejectNonTodayDate(request.standupDate(), today);
        FieldSet fields = requireAtLeastOneField(request);
        Long teamId = TeamIds.of(actor);
        return standupRepository
                .findByUser_IdAndTeam_IdAndStandupDate(actor.getId(), teamId, today)
                .map(existing -> {
                    existing.rewrite(fields.done(), fields.doing(), fields.blockers());
                    return new StandupWriteResult(toResponse(existing, actor, today), false);
                })
                .orElseGet(() -> createToday(actor, today, fields));
    }

    @Transactional
    public StandupResponse update(long id, UpsertStandupRequest request) {
        User actor = currentUserService.requireUser();
        LocalDate today = todayUtc();
        Standup standup = requireOnTeam(id, actor);
        if (!standup.isAuthoredBy(actor) || !standup.isForDate(today)) {
            throw new ForbiddenException();
        }
        rejectNonTodayDate(request.standupDate(), today);
        String done = request.done() != null ? normalize(request.done()) : standup.getDone();
        String doing = request.doing() != null ? normalize(request.doing()) : standup.getDoing();
        String blockers = request.blockers() != null ? normalize(request.blockers()) : standup.getBlockers();
        if (done == null && doing == null && blockers == null) {
            throw new BadRequestException(AT_LEAST_ONE_FIELD);
        }
        standup.rewrite(done, doing, blockers);
        return toResponse(standup, actor, today);
    }

    @Transactional(readOnly = true)
    public List<StandupResponse> listOwn() {
        User actor = currentUserService.requireUser();
        LocalDate today = todayUtc();
        return standupRepository.findOwnOnTeam(actor.getId(), TeamIds.of(actor)).stream()
                .map(standup -> toResponse(standup, actor, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public StandupResponse get(long id) {
        User actor = currentUserService.requireUser();
        Standup standup = requireOnTeam(id, actor);
        if (!canView(actor, standup)) {
            throw new ForbiddenException();
        }
        return toResponse(standup, actor, todayUtc());
    }

    @Transactional(readOnly = true)
    public LeadStandupBoardResponse boardForLead(LocalDate date) {
        User actor = requireLead();
        LocalDate boardDate = date == null ? todayUtc() : date;
        Long teamId = TeamIds.of(actor);
        LocalDate today = todayUtc();
        List<StandupResponse> submitted = standupRepository
                .findByTeamIdAndStandupDateWithUser(teamId, boardDate)
                .stream()
                .sorted(Comparator.comparing(standup -> standup.getUser().getDisplayName()))
                .map(standup -> toResponse(standup, actor, today))
                .toList();
        Set<Long> submittedUserIds = submitted.stream()
                .map(StandupResponse::userId)
                .collect(Collectors.toSet());
        List<StandupMemberResponse> missing = userRepository.findByTeamId(teamId).stream()
                .filter(user -> !submittedUserIds.contains(user.getId()))
                .sorted(Comparator.comparing(User::getDisplayName))
                .map(StandupMemberResponse::from)
                .toList();
        return new LeadStandupBoardResponse(boardDate, submitted, missing);
    }

    private StandupWriteResult createToday(User actor, LocalDate today, FieldSet fields) {
        Standup created = standupRepository.save(Standup.onAuthorTeam(
                actor,
                today,
                fields.done(),
                fields.doing(),
                fields.blockers()));
        return new StandupWriteResult(toResponse(created, actor, today), true);
    }

    private User requireLead() {
        User actor = currentUserService.requireUser();
        if (actor.getRole() != Role.TEAM_LEAD) {
            throw new ForbiddenException();
        }
        return actor;
    }

    private Standup requireOnTeam(long id, User actor) {
        return standupRepository.findByIdAndTeamIdWithUser(id, TeamIds.of(actor))
                .orElseThrow(NotFoundException::new);
    }

    private static boolean canView(User actor, Standup standup) {
        return actor.getRole() == Role.TEAM_LEAD || standup.isAuthoredBy(actor);
    }

    private static void rejectNonTodayDate(LocalDate requestedDate, LocalDate today) {
        if (requestedDate != null && !requestedDate.equals(today)) {
            throw new ForbiddenException();
        }
    }

    private static FieldSet requireAtLeastOneField(UpsertStandupRequest request) {
        String done = normalize(request.done());
        String doing = normalize(request.doing());
        String blockers = normalize(request.blockers());
        if (done == null && doing == null && blockers == null) {
            throw new BadRequestException(AT_LEAST_ONE_FIELD);
        }
        return new FieldSet(done, doing, blockers);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static StandupResponse toResponse(Standup standup, User actor, LocalDate today) {
        return StandupResponse.from(standup, actor, today);
    }

    private record FieldSet(String done, String doing, String blockers) {
    }
}
