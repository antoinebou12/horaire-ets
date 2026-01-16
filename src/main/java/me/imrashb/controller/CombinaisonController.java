package me.imrashb.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.imrashb.domain.ParametresCombinaison;
import me.imrashb.domain.combinaison.CombinaisonHoraire;
import me.imrashb.domain.combinaison.comparator.CombinaisonHoraireComparator;
import me.imrashb.service.CombinaisonService;
import me.imrashb.utils.HoraireImageMaker;
import me.imrashb.utils.HoraireImageMakerTheme;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/combinaisons")
@Tag(name = "Schedule Combinations", description = "API for generating and retrieving schedule combinations")
public class CombinaisonController {

    private static final int MAX_IDS = 64;
    private final CombinaisonService service;

    // Prometheus metrics
    private final Timer apiImagesGenerationTimer;
    private final Timer imageGenerationTimer;
    private final MeterRegistry registry;

    public CombinaisonController(CombinaisonService service,
                                 Counter apiImagesGeneratedCounter,
                                 Timer apiImagesGenerationTimer,
                                 Timer imageGenerationTimer,
                                 MeterRegistry registry) {
        this.service = service;
        this.apiImagesGenerationTimer = apiImagesGenerationTimer;
        this.imageGenerationTimer = imageGenerationTimer;
        this.registry = registry;
    }

    @GetMapping("")
    @Operation(
            summary = "Get schedule combinations",
            description = "Generate and retrieve schedule combinations based on provided parameters. " +
                    "Supports filtering by courses, programmes, and custom sorting criteria."
    )
    public List<CombinaisonHoraire> getCombinaisonsHoraire(
            @Parameter(description = "Combination parameters (courses, programmes, sorting, etc.)")
            ParametresCombinaison parametres) throws RuntimeException {

        CombinaisonHoraireComparator comparator = null;

        if (parametres.getSort() != null && parametres.getSort().size() > 0) {
            CombinaisonHoraireComparator.Builder builder = new CombinaisonHoraireComparator.Builder();
            for (CombinaisonHoraireComparator.Comparator c : parametres.getSort()) {
                try {
                    builder.addComparator(c.getComparatorClass());
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                         IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            comparator = builder.build();
        }


        List<CombinaisonHoraire> combinaisons = service.getCombinaisonsHoraire(parametres);

        if (comparator != null) Collections.sort(combinaisons, comparator);

        return combinaisons;
    }

    @GetMapping("sort")
    @Operation(
            summary = "Get available sorters",
            description = "Retrieve a list of all available sorting comparators for schedule combinations."
    )
    public CombinaisonHoraireComparator.Comparator[] getCombinaisonHoraireSorters() {
        return service.getAvailableCombinaisonHoraireComparators();
    }

    @GetMapping(value = "{id}", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(
            summary = "Get schedule combination image",
            description = "Generate and retrieve a JPEG image of a schedule combination. " +
                    "The combination is identified by an encoded ID. Optional theme parameter for customization."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Image generated successfully",
                    content = @Content(mediaType = MediaType.IMAGE_JPEG_VALUE)),
            @ApiResponse(responseCode = "400", description = "Invalid ID or theme parameter"),
            @ApiResponse(responseCode = "500", description = "Error generating image")
    })
    public @ResponseBody byte[] getCombinaisonImage(
            @Parameter(description = "Encoded combination ID", required = true, example = "abc123")
            @PathVariable String id,
            @Parameter(description = "Optional theme identifier for image styling", example = "dark")
            @RequestParam(required = false) String theme) throws ExecutionException, InterruptedException, IOException {

        // Security: Validate ID input
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
        if (id.length() > 1000) {
            throw new IllegalArgumentException("ID exceeds maximum length");
        }

        // Security: Validate theme parameter length
        if (theme != null && theme.length() > 100) {
            throw new IllegalArgumentException("Theme parameter exceeds maximum length");
        }

        Timer.Sample sample = Timer.start(registry);
        try {
            HoraireImageMakerTheme imageTheme = HoraireImageMaker.getThemeFromId(theme);
            String themeName = theme != null ? theme : "default";

            CombinaisonHoraire comb = service.getCombinaisonFromEncodedId(id);
            Future<Image> future = new HoraireImageMaker(comb, imageTheme, imageGenerationTimer, registry).drawHoraire();
            Image img = future.get();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write((RenderedImage) img, "jpeg", os);

            // Record metrics
            registry.counter("api_images_generated_total",
                    "theme", themeName
            ).increment();

            sample.stop(apiImagesGenerationTimer);
            return os.toByteArray();
        } catch (ExecutionException | InterruptedException | IOException e) {
            sample.stop(apiImagesGenerationTimer);
            throw e;
        } catch (RuntimeException e) {
            sample.stop(apiImagesGenerationTimer);
            throw e;
        } catch (Exception e) {
            sample.stop(apiImagesGenerationTimer);
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "id")
    @Operation(
            summary = "Get combinations by encoded IDs",
            description = "Retrieve multiple schedule combinations by their encoded IDs. " +
                    "Maximum " + MAX_IDS + " IDs allowed per request."
    )
    public List<CombinaisonHoraire> getCombinaisonsFromEncodedId(
            @Parameter(description = "Array of encoded combination IDs (max " + MAX_IDS + ")", required = true, example = "[\"abc123\", \"def456\"]")
            @RequestParam String[] ids) throws ExecutionException, InterruptedException, IOException {

        // Security: Enforce maximum number of IDs to prevent DoS attacks
        if (ids.length > MAX_IDS) {
            throw new IllegalArgumentException("Maximum " + MAX_IDS + " IDs allowed per request");
        }

        // Security: Validate each ID is not empty and within reasonable length
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("ID cannot be null or empty");
            }
            if (id.length() > 1000) { // Reasonable limit for Base64 encoded IDs
                throw new IllegalArgumentException("ID exceeds maximum length");
            }
        }

        List<CombinaisonHoraire> combinaisons = new ArrayList<>();

        for (String id : ids) {
            try {
                CombinaisonHoraire comb = service.getCombinaisonFromEncodedId(id);
                combinaisons.add(comb);
            } catch (Exception ex) {
                // Ignore invalid IDs but log for monitoring
                // Note: In production, use proper logging framework instead of silent ignore
            }
        }

        return combinaisons;
    }

}
