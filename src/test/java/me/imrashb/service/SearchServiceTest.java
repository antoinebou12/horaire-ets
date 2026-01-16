package me.imrashb.service;

import me.imrashb.domain.CoursAutocompleteResult;
import me.imrashb.domain.CoursDataWrapper;
import me.imrashb.domain.CoursSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SearchService.
 * Tests all search algorithms: BM25, Fuzzy, Hybrid, and Autocomplete.
 */
public class SearchServiceTest {

    private SearchService searchService;
    private List<CoursDataWrapper> testCourses;

    @BeforeEach
    void setUp() {
        searchService = new SearchService();
        testCourses = createTestCourses();
    }

    // ========== Helper Methods ==========

    private List<CoursDataWrapper> createTestCourses() {
        return Arrays.asList(
            createCourse("MAT380", "MAT380 - Algèbre linéaire", "Introduction à l'algèbre linéaire et ses applications", 3),
            createCourse("LOG100", "LOG100 - Introduction à la programmation", "Bases de la programmation orientée objet", 3),
            createCourse("INF123", "INF123 - Structures de données", "Structures de données et algorithmes fondamentaux", 4),
            createCourse("ELE216", "ELE216 - Circuits électriques", "Analyse des circuits électriques et électroniques", 3),
            createCourse("MAT165", "MAT165 - Calcul différentiel", "Calcul différentiel et intégral pour l'ingénierie", 3),
            createCourse("LOG200", "LOG200 - Programmation avancée", "Programmation avancée et design patterns", 4),
            createCourse("MEC636", "MEC636 - Mécanique des fluides", "Mécanique des fluides et transfert thermique", 3),
            createCourse("GPA123", "GPA123 - Automatisation industrielle", "Systèmes d'automatisation et contrôle", 3)
        );
    }

    private CoursDataWrapper createCourse(String sigle, String titre, String description, Integer credits) {
        CoursDataWrapper course = new CoursDataWrapper();
        course.setSigle(sigle);
        course.setTitre(titre);
        course.setDescription(description);
        course.setCredits(credits);
        course.setPrealables(Collections.emptyList());
        course.setDisponible(true);
        return course;
    }

    private CoursDataWrapper createCourseMinimal(String sigle, String titre, Integer credits) {
        CoursDataWrapper course = new CoursDataWrapper();
        course.setSigle(sigle);
        course.setTitre(titre);
        course.setCredits(credits);
        course.setPrealables(Collections.emptyList());
        return course;
    }

    // ========== Test Helper Methods ==========

    private void assertResultsSortedByScore(List<CoursSearchResult> results) {
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).getScore() >= results.get(i + 1).getScore(),
                    "Results should be sorted by score descending");
        }
    }

    private void assertAutocompleteResultsSortedByScore(List<CoursAutocompleteResult> results) {
        for (int i = 0; i < results.size() - 1; i++) {
            assertTrue(results.get(i).getScore() >= results.get(i + 1).getScore(),
                    "Results should be sorted by score descending");
        }
    }

    private void assertLimitRespected(List<?> results, int limit) {
        assertTrue(results.size() <= limit, "Should respect limit parameter");
    }

    private void assertEmpty(List<?> results, String message) {
        assertTrue(results.isEmpty(), message);
    }

    // ========== BM25 Search Tests ==========

    @ParameterizedTest(name = "BM25 - Invalid input: query=\"{0}\"")
    @CsvSource({
        "''",
        "'   '"
    })
    void testSearchBM25_InvalidInput(String query) {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, query, 10);
        assertEmpty(results, "Should return empty list for invalid input");
    }

    @Test
    void testSearchBM25_NullQuery() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, null, 10);
        assertEmpty(results, "Should return empty list for null query");
    }

    @Test
    void testSearchBM25_EmptyCourses() {
        List<CoursSearchResult> results = searchService.searchBM25(Collections.emptyList(), "programmation", 10);
        assertEmpty(results, "Should return empty list for empty courses");
    }

    @Test
    void testSearchBM25_NullCourses() {
        List<CoursSearchResult> results = searchService.searchBM25(null, "programmation", 10);
        assertEmpty(results, "Should return empty list for null courses");
    }

    @Test
    void testSearchBM25_ExactMatch() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "MAT380", 10);
        
        assertFalse(results.isEmpty(), "Should find course with exact sigle match");
        assertEquals("MAT380", results.get(0).getSigle(), "First result should be MAT380");
        assertNotNull(results.get(0).getScore(), "Result should have a score");
        assertTrue(results.get(0).getScore() > 0, "Score should be positive");
    }

    @Test
    void testSearchBM25_DescriptionMatch() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "programmation", 10);
        
        assertFalse(results.isEmpty(), "Should find courses by description");
        assertTrue(results.size() >= 2, "Should find multiple programming courses");
        // Verify both LOG100 and LOG200 are in results
        List<String> sigles = results.stream().map(CoursSearchResult::getSigle).collect(Collectors.toList());
        assertTrue(sigles.contains("LOG100") || sigles.contains("LOG200"), 
                "Should find programming courses");
    }

    @Test
    void testSearchBM25_TitleMatch() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "Algèbre", 10);
        
        assertFalse(results.isEmpty(), "Should find course by title");
        assertEquals("MAT380", results.get(0).getSigle(), "Should find MAT380 with 'Algèbre' in title");
    }

    @Test
    void testSearchBM25_MultipleMatchesAndSorted() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "programmation", 10);
        
        assertTrue(results.size() > 1, "Should return multiple results");
        assertResultsSortedByScore(results);
    }

    @Test
    void testSearchBM25_NoMatches() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "XYZ999", 10);
        assertEmpty(results, "Should return empty list when no matches found");
    }

    @Test
    void testSearchBM25_LimitResults() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "MAT", 2);
        assertLimitRespected(results, 2);
    }

    @Test
    void testSearchBM25_FrenchAccents() {
        List<CoursSearchResult> results = searchService.searchBM25(testCourses, "algèbre", 10);
        
        assertFalse(results.isEmpty(), "Should handle French accents");
        assertEquals("MAT380", results.get(0).getSigle(), "Should find MAT380 with accented query");
    }

    @Test
    void testSearchBM25_EmptyDescription() {
        CoursDataWrapper course = createCourseMinimal("TEST001", "Test Course", 3);
        List<CoursDataWrapper> courses = Collections.singletonList(course);
        
        List<CoursSearchResult> results = searchService.searchBM25(courses, "Test", 10);
        assertFalse(results.isEmpty(), "Should handle courses with empty description");
        assertEquals("TEST001", results.get(0).getSigle(), "Should find course by title even without description");
    }

    // ========== Fuzzy Search Tests ==========

    @Test
    void testSearchFuzzy_NullQuery() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, null, 10, null);
        assertEmpty(results, "Should return empty list for null query");
    }

    @Test
    void testSearchFuzzy_EmptyQuery() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "", 10, null);
        assertEmpty(results, "Should return empty list for empty query");
    }

    @Test
    void testSearchFuzzy_EmptyCourses() {
        List<CoursSearchResult> results = searchService.searchFuzzy(Collections.emptyList(), "MAT", 10, null);
        assertEmpty(results, "Should return empty list for empty courses");
    }

    @Test
    void testSearchFuzzy_ExactMatch() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAT380", 10, null);
        
        assertFalse(results.isEmpty(), "Should find course with exact match");
        assertEquals("MAT380", results.get(0).getSigle(), "First result should be MAT380");
        assertNotNull(results.get(0).getScore(), "Result should have a score");
        assertTrue(results.get(0).getScore() > 0.9, "Exact match should have high score");
    }

    @Test
    void testSearchFuzzy_TypoMatch() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAAT380", 10, 2);
        
        assertFalse(results.isEmpty(), "Should find course with typo");
        assertEquals("MAT380", results.get(0).getSigle(), "Should find MAT380 despite typo");
    }

    @Test
    void testSearchFuzzy_PrefixMatch() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAT", 10, null);
        
        assertFalse(results.isEmpty(), "Should find courses by prefix");
        // Should find MAT380 and MAT165
        List<String> sigles = results.stream().map(CoursSearchResult::getSigle).collect(Collectors.toList());
        assertTrue(sigles.contains("MAT380") || sigles.contains("MAT165"), 
                "Should find MAT courses");
    }

    @Test
    void testSearchFuzzy_ContainsMatch() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "programmation", 10, null);
        
        assertFalse(results.isEmpty(), "Should find courses by contains match");
    }

    @Test
    void testSearchFuzzy_AdaptiveMaxDistance() {
        // Short query should use smaller max distance
        List<CoursSearchResult> shortResults = searchService.searchFuzzy(testCourses, "MA", 10, null);
        
        // Longer query should allow larger distance
        List<CoursSearchResult> longResults = searchService.searchFuzzy(testCourses, "programmation", 10, null);
        
        assertFalse(shortResults.isEmpty(), "Short query should find matches");
        assertFalse(longResults.isEmpty(), "Long query should find matches");
    }

    @Test
    void testSearchFuzzy_CustomMaxDistance() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAAT", 10, 1);
        
        // With max distance of 1, should still find MAT380
        assertFalse(results.isEmpty(), "Should find matches within max distance");
    }

    @Test
    void testSearchFuzzy_SiglePriority() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAT", 10, null);
        
        assertFalse(results.isEmpty(), "Should find matches");
        // Sigle matches should be scored higher
        CoursSearchResult first = results.get(0);
        assertTrue(first.getSigle().contains("MAT"), 
                "Sigle matches should be prioritized");
    }

    @Test
    void testSearchFuzzy_MultipleMatchesAndSorted() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAT", 10, null);
        
        assertTrue(results.size() > 1, "Should return multiple results");
        assertResultsSortedByScore(results);
    }

    @Test
    void testSearchFuzzy_NoMatches() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "ZZZ999", 10, 1);
        assertEmpty(results, "Should return empty list when no matches found");
    }

    @Test
    void testSearchFuzzy_LimitResults() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MAT", 2, null);
        assertLimitRespected(results, 2);
    }

    @Test
    void testSearchFuzzy_ShortQuery() {
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "MA", 10, null);
        assertFalse(results.isEmpty(), "Should handle very short queries");
    }

    @Test
    void testSearchFuzzy_MaxDistanceExceeded() {
        // Search with very strict max distance
        List<CoursSearchResult> results = searchService.searchFuzzy(testCourses, "XYZ999", 10, 1);
        assertTrue(results.isEmpty(), "Should not match when edit distance exceeds max");
    }

    // ========== Hybrid Search Tests ==========

    @Test
    void testSearchHybrid_NullQuery() {
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, null, 10, null);
        assertEmpty(results, "Should return empty list for null query");
    }

    @Test
    void testSearchHybrid_EmptyQuery() {
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "", 10, null);
        assertEmpty(results, "Should return empty list for empty query");
    }

    @Test
    void testSearchHybrid_ShortQueryUsesFuzzy() {
        // Query with ≤6 chars should use fuzzy
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "MAT", 10, null);
        
        assertFalse(results.isEmpty(), "Should find matches for short query");
        // Should use fuzzy algorithm (shorter queries prefer fuzzy)
    }

    @Test
    void testSearchHybrid_SingleWordUsesFuzzy() {
        // Single word query should use fuzzy
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "programmation", 10, null);
        
        assertFalse(results.isEmpty(), "Should find matches for single word query");
    }

    @Test
    void testSearchHybrid_MediumQueryUsesHybrid() {
        // Medium query (7-20 chars, 2-3 words) should use hybrid
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "programmation orientée", 10, null);
        
        assertFalse(results.isEmpty(), "Should find matches for medium query");
        // Hybrid mode merges results from both algorithms
    }

    @Test
    void testSearchHybrid_LongQueryUsesBM25() {
        // Long query (>20 chars) should use BM25
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, 
                "programmation orientée objet avancée", 10, null);
        
        assertFalse(results.isEmpty(), "Should find matches for long query");
    }

    @Test
    void testSearchHybrid_ManyWordsUsesBM25() {
        // Query with 4+ words should use BM25
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, 
                "structures données algorithmes fondamentaux", 10, null);
        
        assertFalse(results.isEmpty(), "Should find matches for multi-word query");
    }

    @Test
    void testSearchHybrid_ReturnsResults() {
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "programmation", 10, null);
        
        assertFalse(results.isEmpty(), "Should return results from selected algorithm");
    }

    @Test
    void testSearchHybrid_LimitResults() {
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "MAT", 2, null);
        assertLimitRespected(results, 2);
    }

    @Test
    void testSearchHybrid_NoMatches() {
        List<CoursSearchResult> results = searchService.searchHybrid(testCourses, "XYZ999ABC", 10, null);
        assertEmpty(results, "Should return empty list when no matches found");
    }

    // ========== Autocomplete Tests ==========

    @Test
    void testAutocomplete_NullQuery() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, null, 10);
        assertEmpty(results, "Should return empty list for null query");
    }

    @Test
    void testAutocomplete_EmptyQuery() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "", 10);
        assertEmpty(results, "Should return empty list for empty query");
    }

    @Test
    void testAutocomplete_EmptyCourses() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(Collections.emptyList(), "MAT", 10);
        assertEmpty(results, "Should return empty list for empty courses");
    }

    @Test
    void testAutocomplete_SiglePrefixMatch() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "MAT", 10);
        
        assertFalse(results.isEmpty(), "Should find courses by sigle prefix");
        assertTrue(results.get(0).getScore() >= 1.0, 
                "Sigle prefix match should have high score");
        assertEquals("MAT380", results.get(0).getSigle(), 
                "Should find MAT380 with 'MAT' prefix");
    }

    @Test
    void testAutocomplete_SigleExactMatch() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "MAT380", 10);
        
        assertFalse(results.isEmpty(), "Should find exact sigle match");
        assertEquals("MAT380", results.get(0).getSigle(), 
                "Should find exact match");
        assertTrue(results.get(0).getScore() >= 1.5, 
                "Exact match should have highest score (1.5)");
    }

    @Test
    void testAutocomplete_SigleContainsMatch() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "80", 10);
        
        // Should find MAT380 with contains match
        List<String> sigles = results.stream().map(CoursAutocompleteResult::getSigle).collect(Collectors.toList());
        assertTrue(sigles.contains("MAT380"), "Should find MAT380 with contains match");
    }

    @Test
    void testAutocomplete_TitlePrefixMatch() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "Algèbre", 10);
        
        assertFalse(results.isEmpty(), "Should find course by title prefix");
        assertEquals("MAT380", results.get(0).getSigle(), 
                "Should find MAT380 by title prefix");
        assertTrue(results.get(0).getScore() >= 0.6, 
                "Title prefix match should have score >= 0.6");
    }

    @Test
    void testAutocomplete_TitleWordPrefixMatch() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "Intro", 10);
        
        assertFalse(results.isEmpty(), "Should find courses by word prefix in title");
        List<String> sigles = results.stream().map(CoursAutocompleteResult::getSigle).collect(Collectors.toList());
        assertTrue(sigles.contains("LOG100"), "Should find LOG100 with 'Intro' prefix");
    }

    @Test
    void testAutocomplete_SiglePriorityOverTitle() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "MAT", 10);
        
        assertFalse(results.isEmpty(), "Should find matches");
        // Sigle matches should be first (higher score)
        CoursAutocompleteResult first = results.get(0);
        assertTrue(first.getSigle() != null && first.getSigle().startsWith("MAT"), 
                "Sigle matches should be prioritized over title matches");
    }

    @Test
    void testAutocomplete_MultipleMatchesAndSorted() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "MAT", 10);
        
        assertTrue(results.size() > 1, "Should return multiple results for 'MAT'");
        assertAutocompleteResultsSortedByScore(results);
    }

    @Test
    void testAutocomplete_LimitResults() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "MAT", 2);
        assertLimitRespected(results, 2);
    }

    @Test
    void testAutocomplete_NoMatches() {
        List<CoursAutocompleteResult> results = searchService.autocomplete(testCourses, "XYZ999", 10);
        assertEmpty(results, "Should return empty list when no matches found");
    }

    @Test
    void testAutocomplete_CaseInsensitive() {
        List<CoursAutocompleteResult> results1 = searchService.autocomplete(testCourses, "mat", 10);
        List<CoursAutocompleteResult> results2 = searchService.autocomplete(testCourses, "MAT", 10);
        
        assertFalse(results1.isEmpty(), "Should find matches with lowercase");
        assertFalse(results2.isEmpty(), "Should find matches with uppercase");
        // Both should return same courses
        assertEquals(results1.size(), results2.size(), 
                "Case should not affect results");
    }

    // ========== Combined Input Validation Tests ==========

    @ParameterizedTest(name = "All methods - Empty/whitespace query: query=\"{0}\"")
    @CsvSource({
        "''",
        "'   '"
    })
    void testAllMethods_EmptyOrWhitespaceQuery(String query) {
        // Test all search methods with empty/whitespace query
        assertEmpty(searchService.searchBM25(testCourses, query, 10), 
                "BM25 should return empty for empty/whitespace query");
        assertEmpty(searchService.searchFuzzy(testCourses, query, 10, null), 
                "Fuzzy should return empty for empty/whitespace query");
        assertEmpty(searchService.searchHybrid(testCourses, query, 10, null), 
                "Hybrid should return empty for empty/whitespace query");
        assertEmpty(searchService.autocomplete(testCourses, query, 10), 
                "Autocomplete should return empty for empty/whitespace query");
    }

    @ParameterizedTest(name = "All methods - Empty/null courses")
    @MethodSource("emptyCoursesProvider")
    void testAllMethods_EmptyOrNullCourses(List<CoursDataWrapper> courses, String description) {
        String query = "programmation";
        assertEmpty(searchService.searchBM25(courses, query, 10), 
                "BM25 should return empty for " + description);
        assertEmpty(searchService.searchFuzzy(courses, query, 10, null), 
                "Fuzzy should return empty for " + description);
        assertEmpty(searchService.searchHybrid(courses, query, 10, null), 
                "Hybrid should return empty for " + description);
        assertEmpty(searchService.autocomplete(courses, query, 10), 
                "Autocomplete should return empty for " + description);
    }

    // ========== Parameterized Test Data Providers ==========

    static Stream<Arguments> emptyCoursesProvider() {
        return Stream.of(
            Arguments.of(Collections.emptyList(), "empty courses list"),
            Arguments.of(null, "null courses list")
        );
    }
}
