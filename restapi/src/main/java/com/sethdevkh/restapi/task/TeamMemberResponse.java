package com.sethdevkh.restapi.task;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.User;

public record TeamMemberResponse(long id, String displayName, Role role) {

    static TeamMemberResponse from(User user) {
        return new TeamMemberResponse(user.getId(), user.getDisplayName(), user.getRole());
    }
}
