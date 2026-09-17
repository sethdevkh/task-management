package com.sethdevkh.restapi.standup;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpsertStandupRequest(
        @Size(max = 4000) String done,
        @Size(max = 4000) String doing,
        @Size(max = 4000) String blockers,
        LocalDate standupDate) {
}
