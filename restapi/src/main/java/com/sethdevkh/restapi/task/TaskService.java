package com.sethdevkh.restapi.task;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.Task;
import com.sethdevkh.restapi.domain.TaskRepository;
import com.sethdevkh.restapi.domain.TaskStatus;
import com.sethdevkh.restapi.domain.TeamIds;
import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;
import com.sethdevkh.restapi.security.CurrentUserService;
import com.sethdevkh.restapi.web.BadRequestException;
import com.sethdevkh.restapi.web.ForbiddenException;
import com.sethdevkh.restapi.web.NotFoundException;

@Service
public class TaskService {

    private final CurrentUserService currentUserService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(
            CurrentUserService currentUserService,
            TaskRepository taskRepository,
            UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listOwn() {
        User actor = currentUserService.requireUser();
        return taskRepository.findOwnOnTeam(TeamIds.of(actor), actor.getId()).stream()
                .map(task -> toResponse(task, actor))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(long id) {
        User actor = currentUserService.requireUser();
        Task task = requireOnTeam(id, actor);
        if (!canViewOrEdit(actor, task)) {
            throw new ForbiddenException();
        }
        return toResponse(task, actor);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        User actor = currentUserService.requireUser();
        User assignee = resolveAssigneeForCreate(actor, request.assigneeId());
        String title = request.title().trim();
        String description = normalizeDescription(request.description());
        TaskStatus status = request.status() == null ? TaskStatus.TO_DO : request.status();
        Task task = taskRepository.save(Task.onCreatorTeam(
                title,
                description,
                status,
                actor,
                assignee,
                request.dueDate()));
        return toResponse(task, actor);
    }

    @Transactional
    public TaskResponse update(long id, UpdateTaskRequest request) {
        User actor = currentUserService.requireUser();
        Task task = requireOnTeam(id, actor);
        if (!canViewOrEdit(actor, task)) {
            throw new ForbiddenException();
        }
        if (request.title() != null) {
            if (!StringUtils.hasText(request.title())) {
                throw new BadRequestException("Title is required");
            }
            task.rename(request.title().trim());
        }
        if (request.description() != null) {
            task.setDescription(normalizeDescription(request.description()));
        }
        if (request.status() != null) {
            task.changeStatus(request.status());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        return toResponse(task, actor);
    }

    @Transactional
    public void delete(long id) {
        User actor = currentUserService.requireUser();
        Task task = requireOnTeam(id, actor);
        if (!canDelete(actor, task)) {
            throw new ForbiddenException();
        }
        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public LeadTaskListResponse listForLead(Long assigneeId, TaskStatus status) {
        User actor = requireLead();
        Long teamId = TeamIds.of(actor);
        List<TaskResponse> tasks = taskRepository.findOnTeamFiltered(teamId, assigneeId, status).stream()
                .map(task -> toResponse(task, actor))
                .toList();
        List<TeamMemberResponse> members = userRepository.findByTeamId(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
        return new LeadTaskListResponse(tasks, members);
    }

    @Transactional
    public TaskResponse assign(long id, AssignTaskRequest request) {
        User actor = requireLead();
        Task task = requireOnTeam(id, actor);
        User assignee = requireTeammate(actor, request.assigneeId());
        try {
            task.assignTo(assignee);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Assignee is not on the team");
        }
        return toResponse(task, actor);
    }

    private User requireLead() {
        User actor = currentUserService.requireUser();
        if (actor.getRole() != Role.TEAM_LEAD) {
            throw new ForbiddenException();
        }
        return actor;
    }

    private Task requireOnTeam(long id, User actor) {
        return taskRepository.findByIdAndTeamIdWithUsers(id, TeamIds.of(actor))
                .orElseThrow(NotFoundException::new);
    }

    private User resolveAssigneeForCreate(User actor, Long assigneeId) {
        if (assigneeId == null) {
            return actor;
        }
        User assignee = requireTeammate(actor, assigneeId);
        if (actor.getRole() == Role.TEAM_MEMBER && !assignee.getId().equals(actor.getId())) {
            throw new ForbiddenException();
        }
        return assignee;
    }

    private User requireTeammate(User actor, Long assigneeId) {
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new BadRequestException("Assignee is not on the team"));
        if (!TeamIds.of(actor).equals(TeamIds.of(assignee))) {
            throw new BadRequestException("Assignee is not on the team");
        }
        return assignee;
    }

    private static boolean canViewOrEdit(User actor, Task task) {
        if (actor.getRole() == Role.TEAM_LEAD) {
            return true;
        }
        return task.isCreatedBy(actor) || task.isAssignedTo(actor);
    }

    private static boolean canDelete(User actor, Task task) {
        if (actor.getRole() == Role.TEAM_LEAD) {
            return true;
        }
        return task.isCreatedBy(actor) && !task.isAssignedToSomeoneElse(actor);
    }

    private static TaskResponse toResponse(Task task, User actor) {
        return TaskResponse.from(task, canDelete(actor, task));
    }

    private static String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        return description.trim();
    }
}
