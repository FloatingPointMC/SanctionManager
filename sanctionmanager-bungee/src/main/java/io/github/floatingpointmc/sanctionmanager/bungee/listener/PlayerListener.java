package io.github.floatingpointmc.sanctionmanager.bungee.listener;

import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

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
    public void onPlayerJoin(LoginEvent event) {
        UUID uuid = event.getConnection().getUniqueId();
        String name = event.getConnection().getName();

        // Save player UUID <-> Name mapping for offline player resolution
        manager.getPlayerService().save(uuid, name);

        // Check for active bans
        Collection<Punishment> active = punishManager.queryActivePunishments(uuid);

        for (Punishment p : active) {
            if (p.getType() == Type.BAN) {
                List<String> lines = TranslationFormatter.isTemporary(toContext(p, name)) ? translationConfig.getStringList("ban.temporary") : translationConfig.getStringList("ban.permanent");
                event.setCancelled(true);
                event.setReason(new TextComponent(TranslationFormatter.format(lines, toContext(p, name))));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(ChatEvent event) {
        if (event.getSender() instanceof ProxiedPlayer player) {
            UUID uuid = player.getUniqueId();
            Collection<Punishment> active = punishManager.queryActivePunishments(uuid);
            for (Punishment p : active) {
                if (p.getType() == Type.MUTE) {
                    List<String> lines = TranslationFormatter.isTemporary(toContext(p, player.getName())) ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");
                    for (String line : TranslationFormatter.formatLines(lines, toContext(p, player.getName()))) {
                        player.sendMessage(new TextComponent(line));
                    }
                    event.setCancelled(true);
                    return;
                }
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