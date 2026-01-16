package me.imrashb.discord.routines;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.*;
import java.util.concurrent.*;

public abstract class PeriodicRoutine implements Runnable {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private Duration period;
    private boolean started = false;
    private ScheduledFuture routine = null;

    // Prometheus metrics (optional - can be null)
    protected final Counter discordRoutineExecutionsCounter;
    protected final Timer discordRoutineExecutionTimer;
    protected final MeterRegistry registry;
    protected final String routineName;

    PeriodicRoutine(Duration period) {
        this(period, null, null, null, "unknown");
    }

    PeriodicRoutine(Duration period, Counter discordRoutineExecutionsCounter, 
                    Timer discordRoutineExecutionTimer, MeterRegistry registry, String routineName) {
        this.period = period;
        this.discordRoutineExecutionsCounter = discordRoutineExecutionsCounter;
        this.discordRoutineExecutionTimer = discordRoutineExecutionTimer;
        this.registry = registry;
        this.routineName = routineName;
    }

    public boolean startRoutine(final Duration initialDelay) {
        if(!started) {
            routine = EXECUTOR.scheduleAtFixedRate(this, initialDelay.getSeconds(), period.getSeconds(), TimeUnit.SECONDS);
            started = true;
            return true;
        } else {
            return false;
        }
    }

    public boolean startRoutine() {
        return startRoutine(Duration.ofSeconds(0));
    }

    public boolean stopRoutine() {
        if(started) {
            routine.cancel(false);
            routine = null;
            started = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void run() {
        Timer.Sample sample = null;
        if (discordRoutineExecutionTimer != null && registry != null) {
            sample = Timer.start(registry);
        }

        try {
            executeRoutine();
            if (discordRoutineExecutionsCounter != null && registry != null) {
                registry.counter("discord_routine_executions_total",
                        "routine", routineName
                ).increment();
            } else if (discordRoutineExecutionsCounter != null) {
                discordRoutineExecutionsCounter.increment();
            }
        } finally {
            if (sample != null && discordRoutineExecutionTimer != null) {
                sample.stop(discordRoutineExecutionTimer);
            }
        }
    }

    /**
     * Subclasses should implement this method instead of overriding run()
     */
    protected abstract void executeRoutine();

}
