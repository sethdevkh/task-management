package com.sethdevkh.restapi.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("select t from Task t where t.team.id = :teamId")
    List<Task> findByTeamId(@Param("teamId") Long teamId);

    @Query("select t from Task t where t.id = :id and t.team.id = :teamId")
    Optional<Task> findByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId);

    @Query("""
            select t from Task t
            join fetch t.assignee
            join fetch t.creator
            where t.team.id = :teamId
              and (t.creator.id = :userId or t.assignee.id = :userId)
            order by t.createdAt desc
            """)
    List<Task> findOwnOnTeam(@Param("teamId") Long teamId, @Param("userId") Long userId);

    @Query("""
            select t from Task t
            join fetch t.assignee
            join fetch t.creator
            where t.team.id = :teamId
              and (:assigneeId is null or t.assignee.id = :assigneeId)
              and (:status is null or t.status = :status)
            order by t.createdAt desc
            """)
    List<Task> findOnTeamFiltered(
            @Param("teamId") Long teamId,
            @Param("assigneeId") Long assigneeId,
            @Param("status") TaskStatus status);

    @Query("""
            select t from Task t
            join fetch t.assignee
            join fetch t.creator
            where t.id = :id and t.team.id = :teamId
            """)
    Optional<Task> findByIdAndTeamIdWithUsers(@Param("id") Long id, @Param("teamId") Long teamId);
}
