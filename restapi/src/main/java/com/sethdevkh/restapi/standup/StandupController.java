package com.sethdevkh.restapi.standup;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/standups")
public class StandupController {

    private final StandupService standupService;

    public StandupController(StandupService standupService) {
        this.standupService = standupService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StandupResponse> listOwn() {
        return standupService.listOwn();
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public StandupResponse get(@PathVariable long id) {
        return standupService.get(id);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StandupResponse> upsertToday(@Valid @RequestBody UpsertStandupRequest request) {
        StandupWriteResult result = standupService.upsertToday(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/standups/" + result.body().id())).body(result.body());
        }
        return ResponseEntity.ok(result.body());
    }

    @PatchMapping(
            path = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public StandupResponse update(@PathVariable long id, @Valid @RequestBody UpsertStandupRequest request) {
        return standupService.update(id, request);
    }
}
