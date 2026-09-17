package com.sethdevkh.restapi.dashboard;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.User;

public record DashboardResponse(
        List<MemberWorkload> workload,
        StatusMix statusMix,
        CompletionRate completion,
        StandupPresence standupPresence) {

    public record MemberWorkload(long id, String displayName, Role role, long toDo, long inProgress) {
    }

    public record StatusMix(
            @JsonProperty("TO_DO") long toDo,
            @JsonProperty("IN_PROGRESS") long inProgress,
            @JsonProperty("COMPLETED") long completed) {
    }

    public record CompletionRate(double rate, long completedInLast7Days, long totalTasks) {
    }

    public record StandupPresence(
            LocalDate date, List<DashboardMember> submitted, List<DashboardMember> missing) {
    }

    public record DashboardMember(long id, String displayName, Role role) {

        static DashboardMember from(User user) {
            return new DashboardMember(user.getId(), user.getDisplayName(), user.getRole());
        }
    }
}
