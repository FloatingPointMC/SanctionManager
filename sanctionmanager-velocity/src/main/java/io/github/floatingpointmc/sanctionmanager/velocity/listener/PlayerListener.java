package io.github.floatingpointmc.sanctionmanager.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.floatingpointmc.sanctionmanager.api.management.PunishmentManagerAPI;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Punishment;
import io.github.floatingpointmc.sanctionmanager.api.punishment.Type;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PlayerListener {
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

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        if (uuid == null) return;
        String name = event.getUsername();

        // Save player UUID <-> Name mapping for offline player resolution
        manager.getPlayerService().save(uuid, name);

        // Check for active bans
        Collection<Punishment> active = punishManager.queryActivePunishments(uuid);

        for (Punishment p : active) {
            if (p.getType() == Type.BAN) {
                List<String> lines = TranslationFormatter.isTemporary(toContext(p, name)) ? translationConfig.getStringList("ban.temporary") : translationConfig.getStringList("ban.permanent");
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(TranslationFormatter.format(lines, toContext(p, name)))));
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Collection<Punishment> active = punishManager.queryActivePunishments(uuid);
        for (Punishment p : active) {
            if (p.getType() == Type.MUTE) {
                List<String> lines = TranslationFormatter.isTemporary(toContext(p, player.getUsername())) ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");
                for (String line : TranslationFormatter.formatLines(lines, toContext(p, player.getUsername()))) {
                    player.sendMessage(Component.text(line));
                }
                event.setResult(PlayerChatEvent.ChatResult.denied());
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