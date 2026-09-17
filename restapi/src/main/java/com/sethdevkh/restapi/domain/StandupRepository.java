package com.sethdevkh.restapi.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StandupRepository extends JpaRepository<Standup, Long> {

    Optional<Standup> findByUser_IdAndStandupDate(Long userId, LocalDate standupDate);

    Optional<Standup> findByUser_IdAndTeam_IdAndStandupDate(Long userId, Long teamId, LocalDate standupDate);

    @Query("select s from Standup s where s.team.id = :teamId and s.standupDate = :standupDate")
    List<Standup> findByTeamIdAndStandupDate(
            @Param("teamId") Long teamId, @Param("standupDate") LocalDate standupDate);

    @Query("select s from Standup s where s.id = :id and s.team.id = :teamId")
    Optional<Standup> findByIdAndTeamId(@Param("id") Long id, @Param("teamId") Long teamId);

    @Query("""
            select s from Standup s
            join fetch s.user
            where s.user.id = :userId and s.team.id = :teamId
            order by s.standupDate desc
            """)
    List<Standup> findOwnOnTeam(@Param("userId") Long userId, @Param("teamId") Long teamId);

    @Query("""
            select s from Standup s
            join fetch s.user
            where s.id = :id and s.team.id = :teamId
            """)
    Optional<Standup> findByIdAndTeamIdWithUser(@Param("id") Long id, @Param("teamId") Long teamId);

    @Query("""
            select s from Standup s
            join fetch s.user
            where s.team.id = :teamId and s.standupDate = :standupDate
            """)
    List<Standup> findByTeamIdAndStandupDateWithUser(
            @Param("teamId") Long teamId, @Param("standupDate") LocalDate standupDate);
}
