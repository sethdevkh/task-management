package com.sethdevkh.restapi.task;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sethdevkh.restapi.domain.TaskStatus;

import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTaskRequest(
        @Size(max = 200) String title,
        @Size(max = 4000) String description,
        TaskStatus status,
        LocalDate dueDate) {
}
