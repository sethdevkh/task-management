package com.sethdevkh.restapi.standup;

import java.time.Instant;
import java.time.LocalDate;

import com.sethdevkh.restapi.domain.Standup;
import com.sethdevkh.restapi.domain.User;

public record StandupResponse(
        long id,
        long userId,
        String displayName,
        LocalDate standupDate,
        String done,
        String doing,
        String blockers,
        Instant createdAt,
        Instant updatedAt,
        boolean editable) {

    static StandupResponse from(Standup standup, User actor, LocalDate todayUtc) {
        User author = standup.getUser();
        boolean editable = standup.isAuthoredBy(actor) && standup.isForDate(todayUtc);
        return new StandupResponse(
                standup.getId(),
                author.getId(),
                author.getDisplayName(),
                standup.getStandupDate(),
                blankToEmpty(standup.getDone()),
                blankToEmpty(standup.getDoing()),
                blankToEmpty(standup.getBlockers()),
                standup.getCreatedAt(),
                standup.getUpdatedAt(),
                editable);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
