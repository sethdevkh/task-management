package com.sethdevkh.restapi.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}
