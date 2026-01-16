package me.imrashb.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.imrashb.domain.*;
import me.imrashb.exception.*;
import me.imrashb.repository.ScrapedCoursDataRepository;
import me.imrashb.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/cours")
@Tag(name = "Courses", description = "API for searching and retrieving course information")
public class CoursController {

    private final CoursService service;
    private final SearchService searchService;
    private final ScrapedCoursDataRepository coursDataRepository;
    private final SessionService sessionService;

    public CoursController(CoursService service,
                          SearchService searchService,
                          ScrapedCoursDataRepository coursDataRepository,
                          SessionService sessionService) {
        this.service = service;
        this.searchService = searchService;
        this.coursDataRepository = coursDataRepository;
        this.sessionService = sessionService;
    }

    @GetMapping("")
    @Operation(summary = "Get all courses", description = "Retrieve a list of all courses, optionally filtered by programme")
    public List<CoursWithoutGroupes> getCours(
            @Parameter(description = "Optional list of programmes to filter by")
            @RequestParam(required = false) List<Programme> programmes) {
        List<CoursWithoutGroupes> cours = service.getCours(programmes);
        if(cours == null) throw new CoursNotInitializedException();
        return cours;
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search courses with hybrid algorithm selection",
            description = "Unified search endpoint that automatically selects the best search algorithm based on query characteristics. " +
                    "Short queries (≤6 chars) or single words use fuzzy matching for typo tolerance. " +
                    "Long queries (>20 chars) or multi-word queries use BM25 for keyword-based relevance. " +
                    "Medium queries use a hybrid approach combining both algorithms. " +
                    "Results are sorted by relevance score in descending order."
    )
    public List<CoursSearchResult> searchCours(
            @Parameter(description = "Search query string", required = true, example = "algorithme")
            @RequestParam String q,
            @Parameter(description = "Maximum number of results to return (default: 20, max: 100)", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "Maximum allowed edit distance for fuzzy matching (default: auto, based on query length)", example = "2")
            @RequestParam(required = false) Integer maxDistance) {
        
        if (!sessionService.isReady()) {
            return Collections.emptyList();
        }

        // Validate and limit the limit parameter
        int validatedLimit = Math.min(Math.max(limit, 1), 100);
        
        // Get all course data from repository
        Iterable<CoursDataWrapper> allCourses = coursDataRepository.findAll();
        List<CoursDataWrapper> coursesList = new ArrayList<>();
        allCourses.forEach(coursesList::add);

        if (coursesList.isEmpty()) {
            return Collections.emptyList();
        }

        // Perform hybrid search (auto-selects algorithm)
        return searchService.searchHybrid(coursesList, q, validatedLimit, maxDistance);
    }


    @GetMapping("/autocomplete")
    @Operation(
            summary = "Autocomplete course search",
            description = "Autocomplete endpoint that performs exact prefix matching on sigles. " +
                    "Returns lightweight results suitable for autocomplete dropdowns. Requires minimum 2 characters."
    )
    public List<CoursAutocompleteResult> autocompleteCours(
            @Parameter(description = "Search query string (minimum 2 characters)", required = true, example = "MAT")
            @RequestParam String q,
            @Parameter(description = "Maximum number of results to return (default: 10, max: 50)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        
        if (!sessionService.isReady()) {
            return Collections.emptyList();
        }

        // Validate and limit the limit parameter (smaller for autocomplete)
        int validatedLimit = Math.min(Math.max(limit, 1), 50);
        
        // Get all course data from repository
        Iterable<CoursDataWrapper> allCourses = coursDataRepository.findAll();
        List<CoursDataWrapper> coursesList = new ArrayList<>();
        allCourses.forEach(coursesList::add);

        if (coursesList.isEmpty()) {
            return Collections.emptyList();
        }

        // Find exact prefix matches on sigle
        String queryUpper = q.toUpperCase();
        List<CoursAutocompleteResult> results = coursesList.stream()
                .filter(course -> course.getSigle() != null && 
                        course.getSigle().toUpperCase().startsWith(queryUpper))
                .map(course -> new CoursAutocompleteResult(
                        course.getSigle(),
                        course.getTitre(),
                        1.0 // Perfect score for exact prefix match
                ))
                .sorted((a, b) -> a.getSigle().compareTo(b.getSigle()))
                .limit(validatedLimit)
                .collect(java.util.stream.Collectors.toList());

        return results;
    }


}
