package io.github.floatingpointmc.sanctionmanager.spigot.listener;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final MinecraftSanctionManager manager;
    private final PunishmentManagerAPI punishManager;
    private final TranslationConfig translationConfig;
    private final TranslationContext contextTemplate;

    public PlayerListener(MinecraftSanctionManager manager, PunishmentManagerAPI punishManager, TranslationConfig translationConfig, TranslationContext contextTemplate) {
        this.manager = manager;
        this.punishManager = punishManager;
        this.translationConfig = translationConfig;
        this.contextTemplate = contextTemplate;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        // Save player UUID <-> Name mapping for offline player resolution
        manager.getPlayerRepository().save(uuid, name);

        // Check for active bans
        Collection<Punishment> active = punishManager.queryActivePunishments(uuid);

        for (Punishment p : active) {
            if (p.getType() == Type.BAN) {
                List<String> lines = TranslationFormatter.isTemporary(toContext(p, name)) ? translationConfig.getStringList("ban.temporary") : translationConfig.getStringList("ban.permanent");
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, TranslationFormatter.format(lines, toContext(p, name)));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Collection<Punishment> active = punishManager.queryActivePunishments(uuid);
        for (Punishment p : active) {
            if (p.getType() == Type.MUTE) {
                List<String> lines = TranslationFormatter.isTemporary(toContext(p, event.getPlayer().getName())) ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");
                for (String line : TranslationFormatter.formatLines(lines, toContext(p, event.getPlayer().getName()))) {
                    event.getPlayer().sendMessage(line);
                }
                event.setCancelled(true);
                return;
            }
        }
    }

    private TranslationContext.Punishment toContext(Punishment p, String targetName) {
        // TODO: Use PlayerRepository to resolve executor UUID to name
        String operatorName = p.getExecutor() != null ? "[Unknown]" : "[Console]";

        return TranslationContext.Punishment.builder()
                .id(p.getId())
                .relId(p.getRelId())
                .target(p.getTarget())
                .targetName(targetName)
                .executor(p.getExecutor() != null ? p.getExecutor() : new UUID(0, 0))
                .operatorName(operatorName)
                .executingTime(p.getExecutingTime())
                .expiryTime(p.getExpiryTime())
                .reason(p.getReason())
                .pluginName(contextTemplate.getPluginName())
                .pluginVersion(contextTemplate.getPluginVersion())
                .build();
    }
}