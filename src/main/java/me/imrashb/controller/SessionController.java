package me.imrashb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.imrashb.domain.*;
import me.imrashb.service.SessionService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@RestController
@RequestMapping("/sessions")
@Tag(name = "Sessions", description = "API for retrieving course sessions and programmes")
public class SessionController {

    private final SessionService service;

    public SessionController(SessionService service) {
        this.service = service;
    }

    @GetMapping("/{session}")
    @Operation(
            summary = "Get courses for a specific session",
            description = "Retrieve all courses for a given session, optionally filtered by programme(s). " +
                    "Session format: YYYYQ (e.g., 20231 for Hiver 2023, 20232 for Été 2023, 20233 for Automne 2023)"
    )
    public List<Cours> getCours(
            @Parameter(description = "Session identifier (format: YYYYQ, e.g., 20231)", required = true, example = "20231")
            @PathVariable String session,
            @Parameter(description = "Optional list of programmes to filter by", example = "LOG")
            @RequestParam(required = false) List<Programme> programmes) {
        List<Cours> cours = service.getListeCours(session);

        if(programmes != null && programmes.size() > 0) {
            Predicate<Cours> byProgramme = (c) -> !Collections.disjoint(c.getProgrammes(), programmes);
            return cours.stream().filter(byProgramme).collect(Collectors.toList());
        } else {
            return cours;
        }
    }

    @GetMapping("/programmes")
    @Operation(
            summary = "Get available programmes",
            description = "Retrieve all available programmes, optionally filtered by session. " +
                    "If no session is provided, returns programmes from all sessions."
    )
    public Set<Programme> getProgrammes(
            @Parameter(description = "Optional session identifier to filter programmes", example = "20231")
            @RequestParam(required = false) String session) {
        return service.getProgrammes(session);
    }

    @GetMapping("")
    @Operation(
            summary = "Get all available sessions",
            description = "Retrieve a list of all available session identifiers. " +
                    "Sessions are in format YYYYQ where Q is 1 (Hiver), 2 (Été), or 3 (Automne)."
    )
    public Set<String> getSessions() {
        return service.getSessions();
    }

}
