package com.sethdevkh.restapi.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private User creator;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Task() {
    }

    private Task(
            String title,
            String description,
            TaskStatus status,
            User creator,
            User assignee,
            Team team,
            LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.creator = creator;
        this.assignee = assignee;
        this.team = team;
        this.dueDate = dueDate;
    }

    /**
     * Opens a task on the creator's team. {@code team_id} is not a caller-supplied
     * argument.
     */
    public static Task onCreatorTeam(
            String title,
            String description,
            TaskStatus status,
            User creator,
            User assignee,
            LocalDate dueDate) {
        Long creatorTeamId = TeamIds.of(creator);
        if (!creatorTeamId.equals(TeamIds.of(assignee))) {
            throw new IllegalArgumentException("Assignee is not on the creator's team");
        }
        TaskStatus resolved = status == null ? TaskStatus.TO_DO : status;
        return new Task(title, description, resolved, creator, assignee, creator.getTeam(), dueDate);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == TaskStatus.COMPLETED && completedAt == null) {
            completedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public User getAssignee() {
        return assignee;
    }

    public User getCreator() {
        return creator;
    }

    public Team getTeam() {
        return team;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
