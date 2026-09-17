package com.sethdevkh.restapi.task;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sethdevkh.restapi.domain.TaskStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lead/tasks")
public class LeadTaskController {

    private final TaskService taskService;

    public LeadTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public LeadTaskListResponse list(
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskStatus status) {
        return taskService.listForLead(assigneeId, status);
    }

    @PatchMapping(
            path = "/{id}/assignee",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TaskResponse assign(@PathVariable long id, @Valid @RequestBody AssignTaskRequest request) {
        return taskService.assign(id, request);
    }
}
