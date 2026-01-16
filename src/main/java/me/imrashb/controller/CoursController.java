package me.imrashb.controller;

import me.imrashb.domain.*;
import me.imrashb.exception.*;
import me.imrashb.repository.ScrapedCoursDataRepository;
import me.imrashb.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/cours")
public class CoursController {

    private final CoursService service;
    private final SearchService searchService;
    private final ScrapedCoursDataRepository coursDataRepository;
    private final SessionService sessionService;
    private final IcsGeneratorService icsGeneratorService;

    public CoursController(CoursService service, SearchService searchService, 
                          ScrapedCoursDataRepository coursDataRepository,
                          SessionService sessionService,
                          IcsGeneratorService icsGeneratorService) {
        this.service = service;
        this.searchService = searchService;
        this.coursDataRepository = coursDataRepository;
        this.sessionService = sessionService;
        this.icsGeneratorService = icsGeneratorService;
    }

    @GetMapping("")
    public List<CoursWithoutGroupes> getCours(@RequestParam(required = false) List<Programme> programmes) {
        List<CoursWithoutGroupes> cours = service.getCours(programmes);
        if(cours == null) throw new CoursNotInitializedException();
        return cours;
    }

    @GetMapping("/search")
    public List<CoursSearchResult> searchCours(
            @RequestParam String query,
            @RequestParam(defaultValue = "hybrid") String algorithm,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Integer maxDistance,
            @RequestParam(required = false) List<Programme> programmes,
            @RequestParam(required = false) Integer minCredits,
            @RequestParam(required = false) Integer maxCredits) {

        // Get all courses from repository
        List<CoursDataWrapper> allCourses = StreamSupport.stream(
            coursDataRepository.findAll().spliterator(), false)
            .collect(Collectors.toList());

        if (allCourses.isEmpty()) {
            return Collections.emptyList();
        }

        // Create search options if any filter is provided
        SearchOptions options = null;
        if ((programmes != null && !programmes.isEmpty()) || minCredits != null || maxCredits != null) {
            options = new SearchOptions(programmes, minCredits, maxCredits);
        }

        // Select search algorithm
        switch (algorithm.toLowerCase()) {
            case "bm25":
                return options != null 
                    ? searchService.searchBM25(allCourses, query, limit, options)
                    : searchService.searchBM25(allCourses, query, limit);
            case "fuzzy":
                return options != null
                    ? searchService.searchFuzzy(allCourses, query, limit, maxDistance, options)
                    : searchService.searchFuzzy(allCourses, query, limit, maxDistance);
            case "hybrid":
            default:
                return options != null
                    ? searchService.searchHybrid(allCourses, query, limit, maxDistance, options)
                    : searchService.searchHybrid(allCourses, query, limit, maxDistance);
        }
    }

    @GetMapping("/autocomplete")
    public List<CoursAutocompleteResult> autocompleteCours(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) List<Programme> programmes,
            @RequestParam(required = false) Integer minCredits,
            @RequestParam(required = false) Integer maxCredits) {

        // Get all courses from repository
        List<CoursDataWrapper> allCourses = StreamSupport.stream(
            coursDataRepository.findAll().spliterator(), false)
            .collect(Collectors.toList());

        if (allCourses.isEmpty()) {
            return Collections.emptyList();
        }

        // Create search options if any filter is provided
        SearchOptions options = null;
        if ((programmes != null && !programmes.isEmpty()) || minCredits != null || maxCredits != null) {
            options = new SearchOptions(programmes, minCredits, maxCredits);
        }

        return options != null
            ? searchService.autocomplete(allCourses, query, limit, options)
            : searchService.autocomplete(allCourses, query, limit);
    }

    @GetMapping(value = "{sigle}/{session}/ics", produces = "text/calendar")
    public ResponseEntity<String> getCoursIcs(
            @PathVariable String sigle,
            @PathVariable String session,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            // Validate dates if both provided
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                return ResponseEntity.badRequest().body("End date must be after start date");
            }

            Set<Cours> coursSet = sessionService.getCoursFromSigles(session, sigle);
            
            if (coursSet == null || coursSet.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Use the first course found (typically there should be only one per session)
            Cours cours = coursSet.iterator().next();
            
            if (cours.getGroupes() == null || cours.getGroupes().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Course has no schedule groups");
            }

            String icsContent = icsGeneratorService.generateIcsFromCours(cours, startDate, endDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/calendar; charset=utf-8"));
            headers.setContentDispositionFormData("attachment", sigle + "-schedule.ics");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(icsContent);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

}
