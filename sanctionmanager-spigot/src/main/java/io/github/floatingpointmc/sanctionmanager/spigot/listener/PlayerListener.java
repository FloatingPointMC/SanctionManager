package io.github.floatingpointmc.sanctionmanager.spigot.listener;

import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftEntryTypes;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final MinecraftSanctionManager manager;
    private final TranslationConfig translationConfig;
    private final TranslationContext contextTemplate;

    public PlayerListener(MinecraftSanctionManager manager, TranslationConfig translationConfig, TranslationContext contextTemplate) {
        this.manager = manager;
        this.translationConfig = translationConfig;
        this.contextTemplate = contextTemplate;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        // Save player UUID <-> Name mapping for offline player resolution
        manager.getPlayerService().save(uuid, name);

        // Check for active bans
        Optional<Entry> active = manager.getEntryService().queryLatestActive(uuid, MinecraftEntryTypes.BAN);
        if (active.isPresent()) {
            Entry entry = active.get();
            List<String> lines = TranslationFormatter.isTemporary(toContext(entry, name)) ? translationConfig.getStringList("ban.temporary") : translationConfig.getStringList("ban.permanent");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, TranslationFormatter.format(lines, toContext(entry, name)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Optional<Entry> active = manager.getEntryService().queryLatestActive(uuid, MinecraftEntryTypes.MUTE);
        if (active.isPresent()) {
            Entry entry = active.get();
            List<String> lines = TranslationFormatter.isTemporary(toContext(entry, event.getPlayer().getName())) ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");
            for (String line : TranslationFormatter.formatLines(lines, toContext(entry, event.getPlayer().getName()))) {
                event.getPlayer().sendMessage(line);
            }
            event.setCancelled(true);
        }
    }

    private TranslationContext.EntryMessage toContext(Entry entry, String targetName) {
        String operatorName = entry.getExecutor() == null ? "[Console]"
                : manager.getPlayerService().findNameByUuid(entry.getExecutor());
        return contextTemplate.forEntry(entry.snapshot(), targetName,
                operatorName == null ? "[Unknown]" : operatorName);
    }
}
