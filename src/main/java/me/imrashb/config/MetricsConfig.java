package me.imrashb.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    // Business metrics
    @Bean
    public Counter scheduleGenerationsCounter(MeterRegistry registry) {
        return Counter.builder("schedule_generations_total")
                .description("Total number of schedule generations")
                .tag("application", "horaire-ets")
                .register(registry);
    }

    @Bean
    public Timer scheduleGenerationTimer(MeterRegistry registry) {
        return Timer.builder("schedule_generation_duration_seconds")
                .description("Time spent generating schedules")
                .register(registry);
    }

    @Bean
    public AtomicLong scheduleCombinationsGenerated(MeterRegistry registry) {
        AtomicLong value = new AtomicLong(0);
        Gauge.builder("schedule_combinations_generated", value, AtomicLong::get)
                .description("Total number of combinations generated")
                .register(registry);
        return value;
    }

    // API metrics
    @Bean
    public Counter apiImagesGeneratedCounter(MeterRegistry registry) {
        return Counter.builder("api_images_generated_total")
                .description("Total number of schedule images generated")
                .register(registry);
    }

    @Bean
    public Timer apiImagesGenerationTimer(MeterRegistry registry) {
        return Timer.builder("api_images_generation_duration_seconds")
                .description("Time spent generating schedule images")
                .register(registry);
    }

    @Bean
    public Counter coursesQueriedCounter(MeterRegistry registry) {
        return Counter.builder("courses_queried_total")
                .description("Total number of course queries")
                .register(registry);
    }

    // Discord bot metrics
    @Bean
    public Counter discordInteractionsCounter(MeterRegistry registry) {
        return Counter.builder("discord_interactions_total")
                .description("Total number of Discord interactions")
                .register(registry);
    }

    @Bean
    public Counter discordInteractionsErrorsCounter(MeterRegistry registry) {
        return Counter.builder("discord_interactions_errors_total")
                .description("Total number of Discord interaction errors")
                .register(registry);
    }

    @Bean
    public Timer discordInteractionProcessingTimer(MeterRegistry registry) {
        return Timer.builder("discord_interaction_processing_duration_seconds")
                .description("Time spent processing Discord interactions")
                .register(registry);
    }

    @Bean
    public AtomicLong discordBotStatus(MeterRegistry registry) {
        AtomicLong value = new AtomicLong(0); // 0 = offline, 1 = online
        Gauge.builder("discord_bot_status", value, AtomicLong::get)
                .description("Discord bot status (0=offline, 1=online)")
                .register(registry);
        return value;
    }

    @Bean
    public Counter discordBotReconnectsCounter(MeterRegistry registry) {
        return Counter.builder("discord_bot_reconnects_total")
                .description("Total number of Discord bot reconnects")
                .register(registry);
    }

    @Bean
    public Counter discordRoutineExecutionsCounter(MeterRegistry registry) {
        return Counter.builder("discord_routine_executions_total")
                .description("Total number of Discord routine executions")
                .register(registry);
    }

    @Bean
    public Timer discordRoutineExecutionTimer(MeterRegistry registry) {
        return Timer.builder("discord_routine_execution_duration_seconds")
                .description("Time spent executing Discord routines")
                .register(registry);
    }

    // Scheduled task metrics
    @Bean
    public Counter scheduledTaskCourseUpdateExecutionsCounter(MeterRegistry registry) {
        return Counter.builder("scheduled_task_course_update_executions_total")
                .description("Total number of course update scheduled task executions")
                .register(registry);
    }

    @Bean
    public Timer scheduledTaskCourseUpdateTimer(MeterRegistry registry) {
        return Timer.builder("scheduled_task_course_update_duration_seconds")
                .description("Time spent executing course update scheduled task")
                .register(registry);
    }

    @Bean
    public Counter scheduledTaskCourseUpdateErrorsCounter(MeterRegistry registry) {
        return Counter.builder("scheduled_task_course_update_errors_total")
                .description("Total number of errors in course update scheduled task")
                .register(registry);
    }

    @Bean
    public AtomicLong scheduledTaskCourseUpdateLastSuccessTimestamp(MeterRegistry registry) {
        AtomicLong value = new AtomicLong(0);
        Gauge.builder("scheduled_task_course_update_last_success_timestamp", value, AtomicLong::get)
                .description("Timestamp of last successful course update (Unix timestamp)")
                .register(registry);
        return value;
    }

    // Image generation metrics
    @Bean
    public Timer imageGenerationTimer(MeterRegistry registry) {
        return Timer.builder("image_generation_duration_seconds")
                .description("Time spent generating images (internal drawing time)")
                .register(registry);
    }

    // API error metrics
    @Bean
    public Counter apiErrorsCounter(MeterRegistry registry) {
        return Counter.builder("api_errors_total")
                .description("Total number of API errors")
                .register(registry);
    }

    @Bean
    public Counter apiErrorsByStatusCounter(MeterRegistry registry) {
        return Counter.builder("api_errors_by_status_total")
                .description("Total number of API errors by HTTP status code")
                .register(registry);
    }

    @Bean
    public Counter apiCombinaisonErrorsCounter(MeterRegistry registry) {
        return Counter.builder("api_combinaison_errors_total")
                .description("Total number of combinaison API errors")
                .register(registry);
    }

    // Sessions available gauge
    @Bean
    public AtomicLong sessionsAvailableCount(MeterRegistry registry) {
        AtomicLong value = new AtomicLong(0);
        Gauge.builder("sessions_available_count", value, AtomicLong::get)
                .description("Number of available sessions")
                .register(registry);
        return value;
    }

}
