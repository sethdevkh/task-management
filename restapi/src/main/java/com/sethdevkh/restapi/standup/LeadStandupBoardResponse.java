package com.sethdevkh.restapi.standup;

import java.time.LocalDate;
import java.util.List;

public record LeadStandupBoardResponse(
        LocalDate date,
        List<StandupResponse> submitted,
        List<StandupMemberResponse> missing) {
}
