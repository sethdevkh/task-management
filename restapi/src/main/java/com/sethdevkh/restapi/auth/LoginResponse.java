package com.sethdevkh.restapi.auth;

import com.sethdevkh.restapi.domain.Role;

public record LoginResponse(String token, Role role, String displayName) {
}
