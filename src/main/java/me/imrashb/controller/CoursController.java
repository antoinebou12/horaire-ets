package me.imrashb.controller;

import io.swagger.v3.oas.annotations.Operation;
import me.imrashb.domain.*;
import me.imrashb.exception.*;
import me.imrashb.repository.ScrapedCoursDataRepository;
import me.imrashb.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/cours")
public class CoursController {

    private final CoursService service;
    private final SearchService searchService;
    private final ScrapedCoursDataRepository coursDataRepository;

    public CoursController(CoursService service, SearchService searchService, ScrapedCoursDataRepository coursDataRepository) {
        this.service = service;
        this.searchService = searchService;
        this.coursDataRepository = coursDataRepository;
    }

    @GetMapping("")
    @Operation(summary = "Get all courses", description = "Retrieve a list of all courses, optionally filtered by programme")
    public List<CoursWithoutGroupes> getCours(@RequestParam(required = false) List<Programme> programmes) {
        List<CoursWithoutGroupes> cours = service.getCours(programmes);
        if(cours == null) throw new CoursNotInitializedException();
        return cours;
    }

    @GetMapping("/search")
    @Operation(
        summary = "Search courses",
        description = "Search courses using BM25, fuzzy, or hybrid search algorithms. " +
                     "Automatically selects the best algorithm based on query length and characteristics."
    )
    public List<CoursSearchResult> searchCours(
            @RequestParam String query,
            @RequestParam(defaultValue = "hybrid") String algorithm,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Integer maxDistance) {
        
        // Get all courses from repository
        List<CoursDataWrapper> allCourses = StreamSupport.stream(
            coursDataRepository.findAll().spliterator(), false)
            .collect(Collectors.toList());

        if (allCourses.isEmpty()) {
            return Collections.emptyList();
        }

        // Select search algorithm
        switch (algorithm.toLowerCase()) {
            case "bm25":
                return searchService.searchBM25(allCourses, query, limit);
            case "fuzzy":
                return searchService.searchFuzzy(allCourses, query, limit, maxDistance);
            case "hybrid":
            default:
                return searchService.searchHybrid(allCourses, query, limit, maxDistance);
        }
    }

    @GetMapping("/autocomplete")
    @Operation(
        summary = "Autocomplete courses",
        description = "Fast autocomplete search optimized for prefix matching on course codes (sigle) and titles. " +
                     "Returns results sorted by relevance with sigle matches prioritized."
    )
    public List<CoursAutocompleteResult> autocompleteCours(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        // Get all courses from repository
        List<CoursDataWrapper> allCourses = StreamSupport.stream(
            coursDataRepository.findAll().spliterator(), false)
            .collect(Collectors.toList());

        if (allCourses.isEmpty()) {
            return Collections.emptyList();
        }

        return searchService.autocomplete(allCourses, query, limit);
    }

}
