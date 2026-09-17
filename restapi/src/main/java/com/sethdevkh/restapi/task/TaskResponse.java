package com.sethdevkh.restapi.task;

import java.time.Instant;
import java.time.LocalDate;

import com.sethdevkh.restapi.domain.Task;
import com.sethdevkh.restapi.domain.TaskStatus;

public record TaskResponse(
        long id,
        String title,
        String description,
        TaskStatus status,
        long assigneeId,
        String assigneeDisplayName,
        long creatorId,
        String creatorDisplayName,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        boolean canDelete) {

    static TaskResponse from(Task task, boolean canDelete) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription() == null ? "" : task.getDescription(),
                task.getStatus(),
                task.getAssignee().getId(),
                task.getAssignee().getDisplayName(),
                task.getCreator().getId(),
                task.getCreator().getDisplayName(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                canDelete);
    }
}
