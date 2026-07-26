package me.thetealviper.commandshop.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import me.thetealviper.commandshop.api.CommandShopApi;
import me.thetealviper.commandshop.model.Price;

public final class CommandCompleter implements TabCompleter {
    private final CommandShopApi plugin;

    public CommandCompleter(CommandShopApi plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
            String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (name.equals("commandshop")) {
                List<String> options = new ArrayList<>(List.of("inspect"));
                if (sender.hasPermission("commandshop.admin")) {
                    options.add("reload");
                    options.add("delete");
                    options.add("unflag");
                }
                return matches(args[0], options);
            }
            if (name.equals("shop")) {
                List<String> options = new ArrayList<>(List.of("check", "inspect"));
                if (sender.hasPermission("commandshop.admin")) {
                    options.add("remove");
                }
                return matches(args[0], options);
            }
            if (name.equals("setprice")) {
                return matches(args[0], List.of("buy", "sell"));
            }
            if (name.equals("buy")) {
                List<String> options =
                        materialNames(plugin.getBuyableMaterials(), true);
                if (sender.hasPermission("commandshop.admin")) {
                    options.add("remove");
                }
                return matches(args[0], options);
            }
            if (name.equals("sell")) {
                List<String> options = sellMaterialNames(sender);
                if (sender.hasPermission("commandshop.admin")) {
                    options.add("remove");
                }
                return matches(args[0], options);
            }
            if (name.equals("price")) {
                Set<Material> materials = new LinkedHashSet<>(plugin.getBuyableMaterials());
                materials.addAll(plugin.getSellableMaterials());
                return matches(args[0], materialNames(materials, true));
            }
        }
        if (args.length == 2) {
            if ((name.equals("commandshop") && args[0].equalsIgnoreCase("inspect"))
                    || (name.equals("shop")
                    && (args[0].equalsIgnoreCase("check")
                    || args[0].equalsIgnoreCase("inspect")))) {
                return matches(args[1], plugin.getKnownPlayerNames());
            }
            if (sender.hasPermission("commandshop.admin")
                    && name.equals("commandshop")
                    && args[0].equalsIgnoreCase("unflag")) {
                return matches(args[1], plugin.getKnownPlayerNames());
            }
            if (sender.hasPermission("commandshop.admin")
                    && name.equals("commandshop")
                    && args[0].equalsIgnoreCase("delete")
                    || sender.hasPermission("commandshop.admin")
                    && name.equals("shop")
                    && args[0].equalsIgnoreCase("remove")) {
                Set<Material> materials = new LinkedHashSet<>(plugin.getBuyableMaterials());
                materials.addAll(plugin.getSellableMaterials());
                return matches(args[1], materialNames(materials, sender instanceof Player));
            }
            if (sender.hasPermission("commandshop.admin")
                    && name.equals("buy") && args[0].equalsIgnoreCase("remove")) {
                return matches(args[1], materialNames(
                        plugin.getBuyableMaterials(), sender instanceof Player));
            }
            if (sender.hasPermission("commandshop.admin")
                    && name.equals("sell") && args[0].equalsIgnoreCase("remove")) {
                return matches(args[1], materialNames(
                        plugin.getSellableMaterials(), sender instanceof Player));
            }
            if (name.equals("setprice")) {
                List<String> all = new ArrayList<>();
                for (Material material : Material.values()) {
                    if (material.isItem() && !material.isAir()) {
                        all.add(material.name().toLowerCase(Locale.ROOT));
                    }
                }
                all.sort(String.CASE_INSENSITIVE_ORDER);
                if (sender instanceof Player) {
                    all.add(0, "hand");
                }
                return matches(args[1], all);
            }
            if (name.equals("buy") || name.equals("sell")) {
                Material material = plugin.resolveMaterial(args[0],
                        sender instanceof Player ? (Player) sender : null);
                if (material == null) {
                    return List.of();
                }
                Price price = name.equals("buy")
                        ? plugin.getBuyPrice(material) : plugin.getSellPrice(material);
                if (price == null) {
                    return List.of();
                }
                List<String> amounts = new ArrayList<>();
                for (int multiplier = 1; multiplier <= 5; multiplier++) {
                    amounts.add(Integer.toString(price.amount() * multiplier));
                }
                amounts.add("max");
                return matches(args[1], amounts);
            }
        }
        if (args.length == 3 && name.equals("setprice")) {
            return matches(args[2], List.of("0", "1", "5", "10", "25", "100"));
        }
        if (args.length == 4 && name.equals("setprice")) {
            return matches(args[3], List.of("1", "8", "16", "32", "64"));
        }
        return List.of();
    }

    private List<String> sellMaterialNames(CommandSender sender) {
        Collection<Material> materials = plugin.getSellableMaterials();
        if (!(sender instanceof Player)
                || !plugin.getConfig().getBoolean("Minimal_Sell_Autocomplete", true)) {
            return materialNames(materials, sender instanceof Player);
        }
        Player player = (Player) sender;
        Set<Material> present = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && plugin.getSellPrice(stack.getType()) != null) {
                present.add(stack.getType());
            }
        }
        return materialNames(present, true);
    }

    private List<String> materialNames(Collection<Material> materials, boolean includeHand) {
        List<String> result = new ArrayList<>();
        for (Material material : materials) {
            result.add(material.name().toLowerCase(Locale.ROOT));
        }
        result.sort(Comparator.naturalOrder());
        if (includeHand) {
            result.add(0, "hand");
        }
        return result;
    }

    private List<String> matches(String token, Collection<String> choices) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(token, choices, result);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
