package me.thetealviper.commandshop.integrations;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.thetealviper.commandshop.api.CommandShopApi;
import me.thetealviper.commandshop.model.Price;
import me.thetealviper.commandshop.model.ShopStat;

public final class CommandShopPlaceholderExpansion extends PlaceholderExpansion {
    private final CommandShopApi plugin;
    private final String identifier;

    public CommandShopPlaceholderExpansion(CommandShopApi plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) {
            return "";
        }
        String lower = params.toLowerCase(Locale.ROOT);
        if (lower.startsWith("buy_price_")) {
            return bundleValue(params.substring("buy_price_".length()), true, true);
        }
        if (lower.startsWith("buy_amount_")) {
            return bundleValue(params.substring("buy_amount_".length()), true, false);
        }
        if (lower.startsWith("buy_unit_price_")) {
            return unitValue(params.substring("buy_unit_price_".length()), true);
        }
        if (lower.startsWith("sell_price_")) {
            return bundleValue(params.substring("sell_price_".length()), false, true);
        }
        if (lower.startsWith("sell_amount_")) {
            return bundleValue(params.substring("sell_amount_".length()), false, false);
        }
        if (lower.startsWith("sell_unit_price_")) {
            return unitValue(params.substring("sell_unit_price_".length()), false);
        }
        if (lower.startsWith("stats_")) {
            return statsValue(player, lower);
        }
        return null;
    }

    private String bundleValue(String materialName, boolean buy, boolean priceValue) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return "";
        }
        Price price = buy ? plugin.getBuyPrice(material) : plugin.getSellPrice(material);
        if (price == null) {
            return "";
        }
        return priceValue ? number(price.price()) : Integer.toString(price.amount());
    }

    private String unitValue(String materialName, boolean buy) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return "";
        }
        Price price = buy ? plugin.getBuyPrice(material) : plugin.getSellPrice(material);
        return price == null ? "" : number(price.price() / price.amount());
    }

    private String statsValue(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        if (params.equals("stats_buy_total_amount")) {
            return number(plugin.getStatTotal(player, "Buy", "amount"));
        }
        if (params.equals("stats_buy_total_money") || params.equals("stats_buy_total_spent")) {
            return number(plugin.getStatTotal(player, "Buy", "money"));
        }
        if (params.equals("stats_sell_total_amount")) {
            return number(plugin.getStatTotal(player, "Sell", "amount"));
        }
        if (params.equals("stats_sell_total_money") || params.equals("stats_sell_total_earned")) {
            return number(plugin.getStatTotal(player, "Sell", "money"));
        }

        String[] pieces = params.split("_");
        if (pieces.length != 5 || !pieces[2].equals("top")) {
            return null;
        }
        int rank;
        try {
            rank = Integer.parseInt(pieces[3]);
        } catch (NumberFormatException exception) {
            return "";
        }
        if (rank <= 0) {
            return "";
        }
        List<ShopStat> entries = plugin.getTopStats(player, pieces[1], rank);
        if (entries.size() < rank) {
            return "";
        }
        ShopStat entry = entries.get(rank - 1);
        if (pieces[4].equals("item")) {
            return entry.material().name();
        }
        if (pieces[4].equals("amount")) {
            return Long.toString(entry.amount());
        }
        if (pieces[4].equals("money")
                || pieces[4].equals("spent")
                || pieces[4].equals("earned")) {
            return number(entry.money());
        }
        return null;
    }

    private String number(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
