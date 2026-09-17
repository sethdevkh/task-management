package com.sethdevkh.restapi.security;

import com.sethdevkh.restapi.domain.Role;

public record AuthenticatedUser(long userId, String email, String displayName, Role role) {
}
