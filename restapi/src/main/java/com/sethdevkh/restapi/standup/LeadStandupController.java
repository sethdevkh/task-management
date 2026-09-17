package com.sethdevkh.restapi.standup;

import java.time.LocalDate;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lead/standups")
public class LeadStandupController {

    private final StandupService standupService;

    public LeadStandupController(StandupService standupService) {
        this.standupService = standupService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public LeadStandupBoardResponse board(@RequestParam(required = false) LocalDate date) {
        return standupService.boardForLead(date);
    }
}
