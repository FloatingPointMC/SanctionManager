package io.github.floatingpointmc.sanctionmanager.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftEntryTypes;
import io.github.floatingpointmc.sanctionmanager.minecraft.MinecraftSanctionManager;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public class PlayerListener {
    private final MinecraftSanctionManager manager;
    private final TranslationConfig translationConfig;
    private final TranslationContext contextTemplate;

    public PlayerListener(MinecraftSanctionManager manager, TranslationConfig translationConfig, TranslationContext contextTemplate) {
        this.manager = manager;
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
        Optional<Entry> active = manager.getEntryService().queryLatestActive(uuid, MinecraftEntryTypes.BAN);
        if (active.isPresent()) {
            Entry entry = active.get();
            List<String> lines = TranslationFormatter.isTemporary(toContext(entry, name)) ? translationConfig.getStringList("ban.temporary") : translationConfig.getStringList("ban.permanent");
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(TranslationFormatter.format(lines, toContext(entry, name)))));
        }
    }

    @SuppressWarnings("deprecation")
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Optional<Entry> active = manager.getEntryService().queryLatestActive(uuid, MinecraftEntryTypes.MUTE);
        if (active.isPresent()) {
            Entry entry = active.get();
            List<String> lines = TranslationFormatter.isTemporary(toContext(entry, player.getUsername())) ? translationConfig.getStringList("mute.temporary") : translationConfig.getStringList("mute.permanent");
            for (String line : TranslationFormatter.formatLines(lines, toContext(entry, player.getUsername()))) {
                player.sendMessage(Component.text(line));
            }
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    private TranslationContext.EntryMessage toContext(Entry entry, String targetName) {
        String operatorName = entry.getExecutor() == null ? "[Console]"
                : manager.getPlayerService().findNameByUuid(entry.getExecutor());
        return contextTemplate.forEntry(entry.snapshot(), targetName,
                operatorName == null ? "[Unknown]" : operatorName);
    }
}
