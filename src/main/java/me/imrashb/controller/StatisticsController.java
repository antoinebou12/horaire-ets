package me.imrashb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.imrashb.domain.*;
import me.imrashb.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/statistics")
@Tag(name = "Statistics", description = "API for retrieving application statistics")
public class StatisticsController {

    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    @GetMapping("")
    @Operation(
            summary = "Get application statistics",
            description = "Retrieve overall statistics about courses, sessions, and other application metrics."
    )
    public Statistics getStatistics() {
        return service.getStatistics();
    }

}
