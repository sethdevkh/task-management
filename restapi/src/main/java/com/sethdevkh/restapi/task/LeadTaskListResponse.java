package com.sethdevkh.restapi.task;

import java.util.List;

public record LeadTaskListResponse(List<TaskResponse> tasks, List<TeamMemberResponse> members) {
}
