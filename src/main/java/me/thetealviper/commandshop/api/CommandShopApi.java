package me.thetealviper.commandshop.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import me.thetealviper.commandshop.model.Price;
import me.thetealviper.commandshop.model.SellQuote;
import me.thetealviper.commandshop.model.ShopStat;

/**
 * Stable application contract used by Bukkit-facing adapters.
 *
 * Keeping adapters on this interface prevents GUI, commands, integrations, and
 * platform compatibility code from depending on the concrete lifecycle class.
 */
public interface CommandShopApi extends Plugin {
    FileConfiguration getConfig();

    Map<Material, Price> getBuyPrices();

    Map<Material, Price> getSellPrices();

    List<Material> getBuyableMaterials();

    List<Material> getSellableMaterials();

    Price getBuyPrice(Material material);

    Price getSellPrice(Material material);

    Material resolveMaterial(String input, Player player);

    List<String> getKnownPlayerNames();

    List<Material> getRecentPurchases(UUID playerId);

    List<ShopStat> getTopStats(OfflinePlayer player, String type, int limit);

    double getStatTotal(OfflinePlayer player, String type, String field);

    double getBalance(Player player);

    String formatMoney(double value);

    String displayName(Material material);

    void send(CommandSender sender, String key, Map<String, String> replacements);

    void notifyOutstandingAbuseFlags(Player recipient);

    boolean purchase(Player player, Material material, int amount);

    SellQuote quoteSellable(Iterable<ItemStack> stacks);

    SellQuote quoteSellable(ItemStack[] stacks);

    boolean executeSaleFromInventory(Player player, SellQuote quote);
}
