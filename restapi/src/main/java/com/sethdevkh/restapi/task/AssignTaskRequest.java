package com.sethdevkh.restapi.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssignTaskRequest(@NotNull Long assigneeId) {
}
