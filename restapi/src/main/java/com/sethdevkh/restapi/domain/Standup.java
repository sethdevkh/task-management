package com.sethdevkh.restapi.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "standups",
        uniqueConstraints = @UniqueConstraint(name = "uk_standups_user_date", columnNames = {"user_id", "standup_date"}))
public class Standup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(name = "standup_date", nullable = false)
    private LocalDate standupDate;

    @Column(length = 4000)
    private String done;

    @Column(length = 4000)
    private String doing;

    @Column(length = 4000)
    private String blockers;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Standup() {
    }

    private Standup(User user, Team team, LocalDate standupDate, String done, String doing, String blockers) {
        this.user = user;
        this.team = team;
        this.standupDate = standupDate;
        this.done = done;
        this.doing = doing;
        this.blockers = blockers;
    }

    /**
     * Opens a standup on the author's team for a UTC calendar date. {@code team_id}
     * is not a caller-supplied argument.
     */
    public static Standup onAuthorTeam(User author, LocalDate standupDateUtc, String done, String doing, String blockers) {
        LocalDate date = standupDateUtc == null ? LocalDate.now(ZoneOffset.UTC) : standupDateUtc;
        return new Standup(author, author.getTeam(), date, done, doing, blockers);
    }

    public void rewrite(String done, String doing, String blockers) {
        this.done = done;
        this.doing = doing;
        this.blockers = blockers;
    }

    public boolean isAuthoredBy(User actor) {
        return actor != null && user != null && actor.getId() != null && actor.getId().equals(user.getId());
    }

    public boolean isForDate(LocalDate date) {
        return date != null && date.equals(standupDate);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Team getTeam() {
        return team;
    }

    public LocalDate getStandupDate() {
        return standupDate;
    }

    public String getDone() {
        return done;
    }

    public String getDoing() {
        return doing;
    }

    public String getBlockers() {
        return blockers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
