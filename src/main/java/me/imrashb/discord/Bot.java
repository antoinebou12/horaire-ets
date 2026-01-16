package me.imrashb.discord;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import me.imrashb.discord.commands.*;
import me.imrashb.discord.events.controller.InteractionHandlerController;
import me.imrashb.discord.events.handler.CommandAutoCompleteInteractionEventHandler;
import me.imrashb.discord.events.handler.ComponentControlledEmbedHandler;
import me.imrashb.discord.events.handler.SlashCommandInteractionEventHandler;
import me.imrashb.discord.routines.*;
import me.imrashb.service.HorairETSService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class Bot {

    private final HorairETSService mediator;
    private final Set<DiscordSlashCommand> commands;
    private JDA jda;

    // Prometheus metrics
    private final AtomicLong discordBotStatus;
    private final Counter discordBotReconnectsCounter;
    private final Counter discordInteractionsCounter;
    private final Counter discordInteractionsErrorsCounter;
    private final Timer discordInteractionProcessingTimer;
    private final Counter discordRoutineExecutionsCounter;
    private final Timer discordRoutineExecutionTimer;
    private final MeterRegistry registry;

    Bot(String token, HorairETSService mediator,
        AtomicLong discordBotStatus,
        Counter discordBotReconnectsCounter,
        Counter discordInteractionsCounter,
        Counter discordInteractionsErrorsCounter,
        Timer discordInteractionProcessingTimer,
        Counter discordRoutineExecutionsCounter,
        Timer discordRoutineExecutionTimer,
        MeterRegistry registry) throws InterruptedException {
        this.mediator = mediator;
        this.commands = new HashSet<>();
        this.discordBotStatus = discordBotStatus;
        this.discordBotReconnectsCounter = discordBotReconnectsCounter;
        this.discordInteractionsCounter = discordInteractionsCounter;
        this.discordInteractionsErrorsCounter = discordInteractionsErrorsCounter;
        this.discordInteractionProcessingTimer = discordInteractionProcessingTimer;
        this.discordRoutineExecutionsCounter = discordRoutineExecutionsCounter;
        this.discordRoutineExecutionTimer = discordRoutineExecutionTimer;
        this.registry = registry;

        JDABuilder builder = JDABuilder.createLight(token);

        if (!this.mediator.getSessionService().isReady()) {
            this.mediator.getSessionService().addSessionManagerReadyListener(ready -> {
                try {
                    if (Bot.this.jda == null && ready) {
                        Bot.this.configure(builder);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            this.configure(builder);
        }

    }

    private void configure(JDABuilder jdaBuilder) throws InterruptedException {
        // Memory usage config
        jdaBuilder.disableCache(Arrays.asList(CacheFlag.values())); // Disable useless caches
        jdaBuilder.setChunkingFilter(ChunkingFilter.NONE);
        jdaBuilder.setMemberCachePolicy(MemberCachePolicy.NONE); // Don't cache users
        jdaBuilder.disableIntents(
                GatewayIntent.GUILD_PRESENCES,
                GatewayIntent.GUILD_MESSAGE_TYPING,
                GatewayIntent.GUILD_VOICE_STATES,
                GatewayIntent.GUILD_BANS,
                GatewayIntent.GUILD_EMOJIS_AND_STICKERS,
                GatewayIntent.GUILD_INVITES,
                GatewayIntent.SCHEDULED_EVENTS);
        jdaBuilder.setLargeThreshold(50);

        try {
            this.jda = jdaBuilder.build().awaitReady();
            // Set bot status to online
            discordBotStatus.set(1);
            log.info("Discord bot initialized successfully");
        } catch(Exception e) {
            // Security: Use proper logging instead of printStackTrace
            log.error("Failed to initialize Discord bot. Reason: {}", e.getMessage(), e);
            discordBotStatus.set(0);
            return;
        }

        // Presence Routine
        new DiscordPresenceRoutine(this.jda, discordRoutineExecutionsCounter, discordRoutineExecutionTimer, registry).startRoutine();

        this.subscribeCommands();
        this.subscribeListeners();

        // Monitor reconnection events
        this.jda.addEventListener(new net.dv8tion.jda.api.hooks.ListenerAdapter() {
            @Override
            public void onStatusChange(net.dv8tion.jda.api.events.StatusChangeEvent event) {
                JDA.Status newStatus = event.getJDA().getStatus();
                JDA.Status oldStatus = event.getOldStatus();
                if (newStatus == net.dv8tion.jda.api.JDA.Status.CONNECTED && 
                    oldStatus != net.dv8tion.jda.api.JDA.Status.CONNECTED) {
                    discordBotReconnectsCounter.increment();
                    discordBotStatus.set(1);
                } else if (newStatus != net.dv8tion.jda.api.JDA.Status.CONNECTED) {
                    discordBotStatus.set(0);
                }
            }
        });
    }

    private void subscribeListeners() {

        InteractionHandlerController interactionHandlerController = new InteractionHandlerController(
                jda,
                discordInteractionsCounter,
                discordInteractionsErrorsCounter,
                discordInteractionProcessingTimer,
                registry);
        interactionHandlerController.addInteractionHandler(new SlashCommandInteractionEventHandler(this.commands));
        interactionHandlerController.addInteractionHandler(new CommandAutoCompleteInteractionEventHandler(this.commands));
        interactionHandlerController.addInteractionHandler(new ComponentControlledEmbedHandler());
        this.jda.addEventListener(interactionHandlerController);
    }

    private void subscribeCommands() {

        // Update commands
        this.jda.updateCommands().complete();

        this.commands.add(new CombinaisonsCommand(mediator));
        this.commands.add(new SessionsCommand(mediator));
        this.commands.add(new HorairETSCommand(mediator, this.commands));
        this.commands.add(new HoraireCommand(mediator));
        for (DiscordSlashCommand c : this.commands) {
            c.subscribeCommand(jda);
        }
    }

}
