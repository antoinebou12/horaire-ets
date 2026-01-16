package me.imrashb.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import me.imrashb.domain.*;
import me.imrashb.domain.combinaison.CombinaisonHoraire;
import me.imrashb.domain.combinaison.comparator.*;
import me.imrashb.exception.CoursNotInitializedException;
import me.imrashb.exception.SessionDoesntExistException;
import me.imrashb.parser.GenerateurHoraire;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@EnableScheduling
@Slf4j
@Scope("singleton")
public class CombinaisonServiceImpl implements CombinaisonService {

    private final SessionService sessionService;

    private final StatisticsService statisticsService;
    private final Set<String> comparators;

    // Prometheus metrics
    private final Counter scheduleGenerationsCounter;
    private final Timer scheduleGenerationTimer;
    private final AtomicLong scheduleCombinationsGenerated;
    private final MeterRegistry registry;

    public CombinaisonServiceImpl(SessionService sessionService, 
                                  StatisticsService statisticsService,
                                  Counter scheduleGenerationsCounter,
                                  Timer scheduleGenerationTimer,
                                  AtomicLong scheduleCombinationsGenerated,
                                  MeterRegistry registry) {
        this.sessionService = sessionService;
        this.statisticsService = statisticsService;
        this.scheduleGenerationsCounter = scheduleGenerationsCounter;
        this.scheduleGenerationTimer = scheduleGenerationTimer;
        this.scheduleCombinationsGenerated = scheduleCombinationsGenerated;
        this.registry = registry;
        this.comparators = new HashSet<>();
        this.initializeComparators();
    }

    private void initializeComparators() {
        this.comparators.add(new LostTimeComparator(null).getId());
        this.comparators.add(new CongesComparator(null).getId());
    }

    @Override
    public List<CombinaisonHoraire> getCombinaisonsHoraire(ParametresCombinaison parametres) {
        long startTime = System.nanoTime();
        Timer.Sample sample = Timer.start(registry);

        try {
            parametres.init(sessionService);

            if (!sessionService.isReady()) {
                throw new CoursNotInitializedException();
            }

            String sessionId = parametres.getSession();

            List<Cours> coursSession = sessionService.getListeCours(sessionId);
            if (coursSession == null)
                throw new SessionDoesntExistException(sessionId);

            List<CombinaisonHoraire> combinaisons = new GenerateurHoraire(coursSession).getCombinaisonsHoraire(parametres);

            long endTime = System.nanoTime();
            long elapsedTime = endTime - startTime;

            // Record Prometheus metrics
            // Get program tags from courses
            Set<Programme> programmes = parametres.getListeCours().stream()
                    .flatMap(c -> c.getProgrammes().stream())
                    .collect(Collectors.toSet());
            
            String programTags = programmes.isEmpty() ? "none" : 
                    programmes.stream()
                            .map(Enum::name)
                            .collect(Collectors.joining(","));
            
            // Increment counter with tags
            registry.counter("schedule_generations_total",
                    "session", sessionId != null ? sessionId : "unknown",
                    "num_courses", String.valueOf(parametres.getNbCours()),
                    "programs", programTags
            ).increment();

            // Update combinations gauge
            scheduleCombinationsGenerated.addAndGet(combinaisons.size());

            // Stop timer
            sample.stop(scheduleGenerationTimer);

            // Update internal statistics (existing functionality)
            this.updateGenerationStatistics(parametres, elapsedTime, combinaisons.size(), sessionId);

            return combinaisons;
        } catch (Exception e) {
            sample.stop(scheduleGenerationTimer);
            throw e;
        }
    }

    private void updateGenerationStatistics(ParametresCombinaison parametres, long timeSpentGenerating, long totalCombinaisonsGenerated, String sessionId) {
        Statistics stats = this.statisticsService.getStatistics();
        stats.addGenerationsPerProgrammes(parametres.getListeCours());
        stats.addTotalCombinaisons(totalCombinaisonsGenerated);
        stats.incrementTotalGenerations();
        stats.addTimeSpentGenerating(timeSpentGenerating);
        stats.addNombreCoursAverage(parametres.getNbCours());
        stats.incrementGenerationsPerSession(sessionId);
        statisticsService.save();
    }

    @Override
    public CombinaisonHoraire getCombinaisonFromEncodedId(String encodedId) {
        return CombinaisonHoraireFactory.fromEncodedUniqueId(encodedId, sessionService);
    }

    @Override
    public CombinaisonHoraireComparator.Comparator[] getAvailableCombinaisonHoraireComparators() {
        return CombinaisonHoraireComparator.Comparator.values();
    }

}
