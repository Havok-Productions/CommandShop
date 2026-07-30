package me.thetealviper.commandshop.commands;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Normalizes mixed-case CommandShop roots before Bukkit/Paper dispatch.
 */
public final class CommandCaseListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String normalized = CommandCaseNormalizer.normalize(event.getMessage());
        if (!normalized.equals(event.getMessage())) {
            event.setMessage(normalized);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        String normalized = CommandCaseNormalizer.normalize(event.getCommand());
        if (!normalized.equals(event.getCommand())) {
            event.setCommand(normalized);
        }
    }
}
