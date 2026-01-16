package me.imrashb.discord.events.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import me.imrashb.discord.events.action.DeferredAction;
import me.imrashb.discord.events.handler.InteractionHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.exceptions.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.ArrayList;
import java.util.List;


@Slf4j
public class InteractionHandlerController extends ListenerAdapter {

    private final List<InteractionHandler> handlers;
    private User owner;

    // Prometheus metrics
    private final Counter discordInteractionsCounter;
    private final Counter discordInteractionsErrorsCounter;
    private final Timer discordInteractionProcessingTimer;
    private final MeterRegistry registry;

    public InteractionHandlerController(JDA jda,
                                       Counter discordInteractionsCounter,
                                       Counter discordInteractionsErrorsCounter,
                                       Timer discordInteractionProcessingTimer,
                                       MeterRegistry registry) {
        this.handlers = new ArrayList<>();
        this.discordInteractionsCounter = discordInteractionsCounter;
        this.discordInteractionsErrorsCounter = discordInteractionsErrorsCounter;
        this.discordInteractionProcessingTimer = discordInteractionProcessingTimer;
        this.registry = registry;
        String ownerId = "231139969089929218";
        jda.retrieveUserById(ownerId).queue(user -> {
            owner = user;
        });
    }

    public void addInteractionHandler(InteractionHandler eventHandler) {
        this.handlers.add(eventHandler);
    }

    public void onGenericEvent(GenericEvent event) {
        Timer.Sample sample = Timer.start(registry);
        String interactionType = "unknown";

        try {
            if (!(event instanceof Interaction)) return;

            Interaction interaction = (Interaction) event;
            interactionType = interaction.getType() != null ? interaction.getType().name() : "unknown";

            // Record interaction counter
            registry.counter("discord_interactions_total",
                    "type", interactionType
            ).increment();

            boolean processed = false;
            for (InteractionHandler handler : handlers) {
                if (handler.isMatchingInteractionType(interaction.getClass())) {
                    DeferredAction action = null;

                    if (handler.isInitialHandler()) {
                        action = handler.process(interaction, null);
                        processed = true;
                    } else {
                        DeferredAction processableAction = handler.getProcessableDeferredAction(interaction);

                        if (processableAction == null) {
                            continue;
                        } else {
                            action = handler.process(interaction, processableAction);
                            processed = true;
                        }
                    }

                    if (action == null) continue;

                    // Add deferrable action to handlers that can process it
                    for (InteractionHandler h : handlers) {
                        if (action.isSupported(h)) {
                            h.addDeferredAction(action);
                        }
                    }

                }
            }

            if (!processed) {
                // TODO UNPROCESSED ACTION
            }

            // Stop timer on success
            sample.stop(discordInteractionProcessingTimer);
        } catch (Exception ex) {
            // Record error metrics
            String errorType = ex.getClass().getSimpleName();
            registry.counter("discord_interactions_errors_total",
                    "type", interactionType,
                    "error_type", errorType
            ).increment();
            sample.stop(discordInteractionProcessingTimer);
            handleDiscordError(ex, event);
        }

    }

    private void handleDiscordError(Exception ex, GenericEvent event) {
        Interaction interaction = (Interaction) event;
        if (event instanceof IReplyCallback) {
            IReplyCallback cb = (IReplyCallback) event;
            if(ex instanceof InsufficientPermissionException) {
                InsufficientPermissionException e = (InsufficientPermissionException) ex;
                cb
                        .reply("Je n'ai pas la permission pour faire cela. Permission manquante: " + e.getPermission().getName())
                        .setEphemeral(true)
                        .queue();
            } else {
                cb.reply("Il y a eu une erreur inattendue. Elle a été signalée. Désolé de cet inconvénient.").setEphemeral(true).queue();

                // Security: Use proper logging instead of printStackTrace
                log.error("Unexpected error in Discord interaction handler", ex);

                try {
                    if (owner != null) {
                        owner.openPrivateChannel().queue((channel) -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Erreur lors d'un évènement").append("\n");
                            if (interaction.getUser() != null) {
                                User user = interaction.getUser();
                                sb.append("Utilisateur: ").append(user.getIdLong()).append("/").append(user.getName()).append("#").append(user.getDiscriminator()).append("\n");
                            }
                            if (interaction.getType() != null)
                                sb.append("Type: ").append(interaction.getType().name()).append("\n");
                            if (interaction.getGuild() != null)
                                sb.append("Guild: ").append(interaction.getGuild().getIdLong()).append("/").append(interaction.getGuild().getName()).append("\n");
                            // Security: Sanitize exception message before sending
                            String safeMessage = ex.getMessage();
                            if (safeMessage == null || safeMessage.length() > 500) {
                                safeMessage = "Error details logged server-side";
                            }
                            sb.append("Message de l'exception: ").append(safeMessage);
                            channel.sendMessage(sb.toString()).queue();
                        }, (failure) -> log.warn("Failed to send error notification to owner", failure));
                    }
                } catch (Exception e) {
                    // Security: Use proper logging instead of printStackTrace
                    log.error("Failed to handle Discord error notification", e);
                }
            }
        }
    }


}
