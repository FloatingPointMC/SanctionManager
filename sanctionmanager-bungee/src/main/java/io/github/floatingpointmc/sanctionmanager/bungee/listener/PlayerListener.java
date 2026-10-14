package io.github.floatingpointmc.sanctionmanager.bungee.listener;

import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftEntryTypes;
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
    public void onPlayerJoin(LoginEvent event) {
        UUID uuid = event.getConnection().getUniqueId();
        String name = event.getConnection().getName();

        // Save player UUID <-> Name mapping for offline player resolution
        manager.getPlayerService().save(uuid, name);

        // Check for active bans
        Optional<Entry> active = manager.getEntryService().queryLatestActive(uuid, MinecraftEntryTypes.BAN);
        if (active.isPresent()) {
            Entry entry = active.get();
            List<String> lines = TranslationFormatter.isTemporary(toContext(entry, name)) ? translationConfig.getStringList("ban.temporary") : translationConfig.getStringList("ban.permanent");
            event.setCancelled(true);
            event.setReason(new TextComponent(TranslationFormatter.format(lines, toContext(entry, name))));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(ChatEvent event) {
        if (event.getSender() instanceof ProxiedPlayer player) {
            UUID uuid = player.getUniqueId();
            Optional<Entry> active = manager.getEntryService().queryLatestActive(uuid, MinecraftEntryTypes.MUTE);
            if (active.isPresent()) {
                Entry entry = active.get();
                List<String> lines = TranslationFormatter.isTemporary(toContext(entry, player.getName())) ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");
                for (String line : TranslationFormatter.formatLines(lines, toContext(entry, player.getName()))) {
                    player.sendMessage(new TextComponent(line));
                }
                event.setCancelled(true);
            }
        }
    }

    private TranslationContext.EntryMessage toContext(Entry entry, String targetName) {
        String operatorName = entry.getExecutor() == null ? "[Console]"
                : manager.getPlayerService().findNameByUuid(entry.getExecutor());
        return contextTemplate.forEntry(entry.snapshot(), targetName,
                operatorName == null ? "[Unknown]" : operatorName);
    }
}
