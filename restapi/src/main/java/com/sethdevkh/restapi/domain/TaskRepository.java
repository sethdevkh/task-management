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
}
