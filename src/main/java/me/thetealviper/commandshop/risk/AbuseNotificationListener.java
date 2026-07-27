package me.thetealviper.commandshop.risk;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import me.thetealviper.commandshop.api.CommandShopApi;

/**
 * Reminds authorized staff about persistent abuse flags after each join.
 */
public final class AbuseNotificationListener implements Listener {
    private final CommandShopApi plugin;

    public AbuseNotificationListener(CommandShopApi plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().execute(plugin,
                () -> plugin.notifyOutstandingAbuseFlags(player),
                null, 20L);
    }
}
