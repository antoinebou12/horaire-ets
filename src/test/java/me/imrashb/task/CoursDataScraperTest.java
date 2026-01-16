package me.imrashb.task;

import me.imrashb.domain.CoursDataWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CoursDataScraper.
 * These tests make actual HTTP requests to the ETS website.
 */
public class CoursDataScraperTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeMAT380() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("MAT380");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "MAT380");
        
        // MAT380 specific assertions
        assertNotNull(data.getTitre(), "Title should not be null");
        assertTrue(data.getTitre().contains("MAT380"), "Title should contain course code");
        assertTrue(data.getCredits() > 0, "Credits should be greater than 0");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        assertNotNull(data.getDescription(), "Description should not be null for available course");
        assertCourseAvailable(data, true);
        
        // Optional fields may be present
        if (data.getResponsable() != null) {
            assertFalse(data.getResponsable().trim().isEmpty(), "Responsable should not be empty if present");
        }
        if (data.getCycle() != null) {
            assertFalse(data.getCycle().trim().isEmpty(), "Cycle should not be empty if present");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeMAT165() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("MAT165");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "MAT165");
        
        assertNotNull(data.getTitre(), "Title should not be null");
        assertTrue(data.getTitre().contains("MAT165"), "Title should contain course code");
        assertTrue(data.getCredits() > 0, "Credits should be greater than 0");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        assertNotNull(data.getDescription(), "Description should not be null for available course");
        assertCourseAvailable(data, true);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeATE800E() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("ATE800E");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "ATE800E");
        
        assertNotNull(data.getTitre(), "Title should not be null");
        assertTrue(data.getTitre().contains("ATE800E"), "Title should contain course code");
        assertTrue(data.getCredits() >= 0, "Credits should be non-negative");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        assertCourseAvailable(data, true);
        
        // English course may have description
        if (data.getDescription() != null) {
            assertFalse(data.getDescription().trim().isEmpty(), "Description should not be empty if present");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeMEC636() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("MEC636");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "MEC636");
        
        assertNotNull(data.getTitre(), "Title should not be null");
        assertTrue(data.getTitre().contains("MEC636"), "Title should contain course code");
        assertTrue(data.getCredits() > 0, "Credits should be greater than 0");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        assertNotNull(data.getDescription(), "Description should not be null for available course");
        assertCourseAvailable(data, true);
        
        // MEC636 should have workload data (Cours + laboratoire)
        // These fields may be present but are optional
        if (data.getChargeTravailCours() != null) {
            assertTrue(data.getChargeTravailCours() > 0, "Cours workload should be positive if present");
        }
        if (data.getChargeTravailLaboratoire() != null) {
            assertTrue(data.getChargeTravailLaboratoire() > 0, "Laboratoire workload should be positive if present");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeMGC868() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("MGC868");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "MGC868");
        
        assertNotNull(data.getTitre(), "Title should not be null");
        assertTrue(data.getTitre().contains("MGC868"), "Title should contain course code");
        assertTrue(data.getCredits() >= 0, "Credits should be non-negative");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        assertCourseAvailable(data, true);
        
        // Description should be present for available courses
        if (data.getDescription() != null) {
            assertFalse(data.getDescription().trim().isEmpty(), "Description should not be empty if present");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeSYS863A25_Unavailable() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("SYS863-A25");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "SYS863-A25");
        
        // Unavailable course should still have basic fields
        assertNotNull(data.getTitre(), "Title should not be null even for unavailable course");
        assertNotNull(data.getCredits(), "Credits should not be null");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        
        // Key assertion: unavailable course should have disponible = false
        assertCourseAvailable(data, false);
        assertFalse(data.getDisponible(), "SYS863-A25 should be marked as unavailable (disponible = false)");
        
        // Description may be null for unavailable courses
        // Other optional fields may also be null
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testScrapeELE440() throws ExecutionException, InterruptedException {
        CoursDataScraper scraper = new CoursDataScraper("ELE440");
        CoursDataWrapper data = scraper.getCoursData(executor).get();

        assertNotNull(data, "Course data should not be null");
        assertBasicCourseData(data, "ELE440");
        
        assertNotNull(data.getTitre(), "Title should not be null");
        assertTrue(data.getTitre().contains("ELE440"), "Title should contain course code");
        assertTrue(data.getCredits() >= 0, "Credits should be non-negative");
        assertNotNull(data.getPrealables(), "Prerequisites list should not be null");
        assertCourseAvailable(data, true);
        
        // Description should be present for available courses
        if (data.getDescription() != null) {
            assertFalse(data.getDescription().trim().isEmpty(), "Description should not be empty if present");
        }
    }

    /**
     * Helper method to assert basic course data fields.
     * Validates sigle, title, and credits are set correctly.
     */
    private void assertBasicCourseData(CoursDataWrapper data, String expectedSigle) {
        assertNotNull(data, "Course data should not be null");
        assertEquals(expectedSigle, data.getSigle(), "Sigle should match expected course code");
        assertNotNull(data.getTitre(), "Title should not be null");
        assertNotNull(data.getCredits(), "Credits should not be null");
        assertTrue(data.getCredits() >= 0, "Credits should be non-negative");
    }

    /**
     * Helper method to assert course availability status.
     * Validates that the disponible field matches the expected value.
     */
    private void assertCourseAvailable(CoursDataWrapper data, boolean expectedAvailable) {
        assertNotNull(data.getDisponible(), "Disponible field should not be null");
        assertEquals(expectedAvailable, data.getDisponible(), 
                String.format("Course availability should be %s", expectedAvailable));
    }
}
