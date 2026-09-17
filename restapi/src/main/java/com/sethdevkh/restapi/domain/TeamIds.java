package com.sethdevkh.restapi.domain;

public final class TeamIds {

    private TeamIds() {
    }

    /**
     * Team comes from the persisted user, never from a request field.
     */
    public static Long of(User user) {
        if (user == null || user.getTeam() == null || user.getTeam().getId() == null) {
            throw new IllegalStateException("User has no team");
        }
        return user.getTeam().getId();
    }
}
