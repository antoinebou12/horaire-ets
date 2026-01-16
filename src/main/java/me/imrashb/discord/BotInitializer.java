package me.imrashb.discord;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import me.imrashb.service.HorairETSService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@EnableScheduling
public class BotInitializer {

    private final HorairETSService mediator;

    // Prometheus metrics
    private final AtomicLong discordBotStatus;
    private final Counter discordBotReconnectsCounter;
    private final Counter discordInteractionsCounter;
    private final Counter discordInteractionsErrorsCounter;
    private final Timer discordInteractionProcessingTimer;
    private final Counter discordRoutineExecutionsCounter;
    private final Timer discordRoutineExecutionTimer;
    private final MeterRegistry registry;

    @Value("${discord.token}")
    private String token;

    public BotInitializer(HorairETSService mediator,
                         AtomicLong discordBotStatus,
                         Counter discordBotReconnectsCounter,
                         Counter discordInteractionsCounter,
                         Counter discordInteractionsErrorsCounter,
                         Timer discordInteractionProcessingTimer,
                         Counter discordRoutineExecutionsCounter,
                         Timer discordRoutineExecutionTimer,
                         MeterRegistry registry) {
        this.mediator = mediator;
        this.discordBotStatus = discordBotStatus;
        this.discordBotReconnectsCounter = discordBotReconnectsCounter;
        this.discordInteractionsCounter = discordInteractionsCounter;
        this.discordInteractionsErrorsCounter = discordInteractionsErrorsCounter;
        this.discordInteractionProcessingTimer = discordInteractionProcessingTimer;
        this.discordRoutineExecutionsCounter = discordRoutineExecutionsCounter;
        this.discordRoutineExecutionTimer = discordRoutineExecutionTimer;
        this.registry = registry;
    }

    @Scheduled(initialDelay = 0, fixedDelay = Long.MAX_VALUE)
    public void initializeDiscordBot() {
        try {
            Bot bot = new Bot(token, mediator,
                    discordBotStatus,
                    discordBotReconnectsCounter,
                    discordInteractionsCounter,
                    discordInteractionsErrorsCounter,
                    discordInteractionProcessingTimer,
                    discordRoutineExecutionsCounter,
                    discordRoutineExecutionTimer,
                    registry);
        } catch (InterruptedException e) {
            System.err.println("Erreur lors de l'initialisation du bot Discord.");
            discordBotStatus.set(0);
            throw new RuntimeException(e);
        }
    }

}
