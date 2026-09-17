package com.sethdevkh.restapi.standup;

import com.sethdevkh.restapi.domain.Role;
import com.sethdevkh.restapi.domain.User;

public record StandupMemberResponse(long id, String displayName, Role role) {

    static StandupMemberResponse from(User user) {
        return new StandupMemberResponse(user.getId(), user.getDisplayName(), user.getRole());
    }
}
