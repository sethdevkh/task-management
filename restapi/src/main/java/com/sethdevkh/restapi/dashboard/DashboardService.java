package com.sethdevkh.restapi.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sethdevkh.restapi.dashboard.DashboardResponse.CompletionRate;
import com.sethdevkh.restapi.dashboard.DashboardResponse.DashboardMember;
import com.sethdevkh.restapi.dashboard.DashboardResponse.MemberWorkload;
import com.sethdevkh.restapi.dashboard.DashboardResponse.StandupPresence;
import com.sethdevkh.restapi.dashboard.DashboardResponse.StatusMix;
import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.Standup;
import com.sethdevkh.restapi.domain.StandupRepository;
import com.sethdevkh.restapi.domain.Task;
import com.sethdevkh.restapi.domain.TaskRepository;
import com.sethdevkh.restapi.domain.TaskStatus;
import com.sethdevkh.restapi.domain.TeamIds;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.CurrentUserService;
import com.sethdevkh.restapi.web.ForbiddenException;

@Service
public class DashboardService {

    static final int COMPLETION_WINDOW_DAYS = 7;

    private final CurrentUserService currentUserService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final StandupRepository standupRepository;

    public DashboardService(
            CurrentUserService currentUserService,
            TaskRepository taskRepository,
            UserRepository userRepository,
            StandupRepository standupRepository) {
        this.currentUserService = currentUserService;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.standupRepository = standupRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse forLead() {
        User actor = requireLead();
        Long teamId = TeamIds.of(actor);
        List<User> teamUsers = userRepository.findByTeamId(teamId).stream()
                .sorted(Comparator.comparing(User::getDisplayName))
                .toList();
        List<Task> tasks = taskRepository.findOnTeamFiltered(teamId, null, null);

        Map<Long, long[]> activeByAssignee = new HashMap<>();
        for (User user : teamUsers) {
            activeByAssignee.put(user.getId(), new long[] {0, 0});
        }

        EnumMap<TaskStatus, Long> mix = new EnumMap<>(TaskStatus.class);
        mix.put(TaskStatus.TO_DO, 0L);
        mix.put(TaskStatus.IN_PROGRESS, 0L);
        mix.put(TaskStatus.COMPLETED, 0L);

        Instant cutoff = Instant.now().minus(COMPLETION_WINDOW_DAYS, ChronoUnit.DAYS);
        long completedInLast7Days = 0;
        for (Task task : tasks) {
            mix.merge(task.getStatus(), 1L, Long::sum);
            long[] counts = activeByAssignee.get(task.getAssignee().getId());
            if (counts != null) {
                if (task.getStatus() == TaskStatus.TO_DO) {
                    counts[0]++;
                } else if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                    counts[1]++;
                }
            }
            if (completedInWindow(task, cutoff)) {
                completedInLast7Days++;
            }
        }

        long totalTasks = tasks.size();
        double rate = totalTasks == 0 ? 0.0 : (double) completedInLast7Days / totalTasks;

        List<MemberWorkload> workload = new ArrayList<>(teamUsers.size());
        for (User user : teamUsers) {
            long[] counts = activeByAssignee.get(user.getId());
            workload.add(new MemberWorkload(
                    user.getId(),
                    user.getDisplayName(),
                    user.getRole(),
                    counts[0],
                    counts[1]));
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Set<Long> submittedIds = new HashSet<>();
        for (Standup standup : standupRepository.findByTeamIdAndStandupDateWithUser(teamId, today)) {
            submittedIds.add(standup.getUser().getId());
        }
        List<DashboardMember> submitted = new ArrayList<>();
        List<DashboardMember> missing = new ArrayList<>();
        for (User user : teamUsers) {
            DashboardMember member = DashboardMember.from(user);
            if (submittedIds.contains(user.getId())) {
                submitted.add(member);
            } else {
                missing.add(member);
            }
        }

        return new DashboardResponse(
                workload,
                new StatusMix(
                        mix.get(TaskStatus.TO_DO),
                        mix.get(TaskStatus.IN_PROGRESS),
                        mix.get(TaskStatus.COMPLETED)),
                new CompletionRate(rate, completedInLast7Days, totalTasks),
                new StandupPresence(today, submitted, missing));
    }

    private User requireLead() {
        User actor = currentUserService.requireUser();
        if (actor.getRole() != Role.TEAM_LEAD) {
            throw new ForbiddenException();
        }
        return actor;
    }

    /**
     * Numerator of the 7-day rate: currently {@code COMPLETED}, with completion
     * time ({@code completedAt}, else {@code updatedAt}) on or after now minus 7
     * days.
     */
    static boolean completedInWindow(Task task, Instant cutoff) {
        if (task.getStatus() != TaskStatus.COMPLETED) {
            return false;
        }
        Instant when = task.getCompletedAt() != null ? task.getCompletedAt() : task.getUpdatedAt();
        return !when.isBefore(cutoff);
    }
}
