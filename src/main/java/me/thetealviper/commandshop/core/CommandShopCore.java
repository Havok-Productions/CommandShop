package me.thetealviper.commandshop.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import me.thetealviper.commandshop.api.CommandShopApi;
import me.thetealviper.commandshop.commands.CommandCompleter;
import me.thetealviper.commandshop.gui.ShopGuiManager;
import me.thetealviper.commandshop.integrations.CommandShopPlaceholderExpansion;
import me.thetealviper.commandshop.model.Price;
import me.thetealviper.commandshop.model.SellQuote;
import me.thetealviper.commandshop.model.ShopStat;

public abstract class CommandShopCore extends JavaPlugin implements CommandShopApi {
    private volatile Map<Material, Price> buyPrices = new ConcurrentHashMap<>();
    private volatile Map<Material, Price> sellPrices = new ConcurrentHashMap<>();
    private volatile Map<String, Material> aliases = new ConcurrentHashMap<>();
    private final Object statsLock = new Object();
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");
    private ExecutorService statsWriter;

    private Economy economy;
    private YamlConfiguration prices;
    private YamlConfiguration stats;
    private YamlConfiguration messages;
    private File pricesFile;
    private File statsFile;
    private File messagesFile;
    private ShopGuiManager guiManager;
    private CommandShopPlaceholderExpansion commandShopExpansion;
    private CommandShopPlaceholderExpansion legacyExpansion;
    private volatile boolean shuttingDown;

    @Override
    public void onEnable() {
        shuttingDown = false;
        statsWriter = createStatsWriter();
        migrateLegacyFiles();
        saveDefaultConfig();
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault has no registered economy provider. CommandShop is disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        pricesFile = new File(getDataFolder(), "prices.db");
        statsFile = new File(getDataFolder(), "stats.db");
        messagesFile = new File(getDataFolder(), "messages.yml");
        prices = loadYaml(pricesFile);
        stats = loadYaml(statsFile);
        messages = loadMessages();

        loadAliases();
        loadPrices();

        guiManager = new ShopGuiManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        CommandCompleter completer = new CommandCompleter(this);
        for (String commandName : List.of("commandshop", "shop", "buy", "sell", "price", "setprice")) {
            if (getCommand(commandName) != null) {
                getCommand(commandName).setTabCompleter(completer);
            }
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            commandShopExpansion = new CommandShopPlaceholderExpansion(this, "commandshop");
            legacyExpansion = new CommandShopPlaceholderExpansion(this, "scoreboardchatshop");
            commandShopExpansion.register();
            legacyExpansion.register();
        }

        getLogger().info("CommandShop enabled with " + buyPrices.size() + " buy prices and "
                + sellPrices.size() + " sell prices.");
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        if (commandShopExpansion != null) {
            commandShopExpansion.unregister();
        }
        if (legacyExpansion != null) {
            legacyExpansion.unregister();
        }
        queueStatsSave();
        if (statsWriter != null) {
            statsWriter.shutdown();
            try {
                if (!statsWriter.awaitTermination(5, TimeUnit.SECONDS)) {
                    getLogger().warning("Timed out while flushing stats.db.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ExecutorService createStatsWriter() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CommandShop-stats-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }
        economy = registration.getProvider();
        return economy != null;
    }

    private void migrateLegacyFiles() {
        File destination = getDataFolder();
        if (destination.exists()) {
            return;
        }
        File pluginsDirectory = destination.getParentFile();
        File legacyDirectory = new File(pluginsDirectory, "ScoreboardChatShop");
        if (!legacyDirectory.isDirectory() && !(legacyDirectory = new File(pluginsDirectory, "SimpleChatShop")).isDirectory()) {
            return;
        }
        if (!destination.mkdirs() && !destination.isDirectory()) {
            getLogger().warning("Could not create the CommandShop data directory for migration.");
            return;
        }
        for (String fileName : List.of("prices.db", "stats.db", "config.yml", "messages.yml")) {
            File source = new File(legacyDirectory, fileName);
            File target = new File(destination, fileName);
            if (!source.isFile() || target.exists()) {
                continue;
            }
            try {
                Files.copy(source.toPath(), target.toPath());
            } catch (IOException exception) {
                getLogger().log(Level.WARNING, "Could not migrate " + fileName, exception);
            }
        }
        getLogger().info("Copied existing shop data from " + legacyDirectory.getName()
                + "; the original files were left untouched.");
    }

    private YamlConfiguration loadYaml(File file) {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent);
                }
                Files.createFile(file.toPath());
            }
            return YamlConfiguration.loadConfiguration(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize " + file.getName(), exception);
        }
    }

    private YamlConfiguration loadMessages() {
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(messagesFile);
        try (InputStreamReader reader = new InputStreamReader(
                getResource("messages.yml"), StandardCharsets.UTF_8)) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(reader));
            loaded.options().copyDefaults(true);
            loaded.save(messagesFile);
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not merge message defaults.", exception);
        }
        return loaded;
    }

    private void loadAliases() {
        Map<String, Material> loadedAliases = new ConcurrentHashMap<>();
        for (Material material : Material.values()) {
            loadedAliases.put(material.name().toLowerCase(Locale.ROOT), material);
        }
        ConfigurationSection section = getConfig().getConfigurationSection("Aliases");
        if (section == null) {
            aliases = loadedAliases;
            return;
        }
        for (String parentName : section.getKeys(false)) {
            Material parent = Material.matchMaterial(parentName);
            if (parent == null) {
                getLogger().warning("Ignoring alias parent with unknown material: " + parentName);
                continue;
            }
            for (String alias : section.getStringList(parentName)) {
                loadedAliases.put(alias.toLowerCase(Locale.ROOT), parent);
            }
        }
        aliases = loadedAliases;
    }

    private void loadPrices() {
        Map<Material, Price> loadedBuyPrices = new ConcurrentHashMap<>();
        Map<Material, Price> loadedSellPrices = new ConcurrentHashMap<>();
        loadPriceSection("Buy", loadedBuyPrices);
        loadPriceSection("Sell", loadedSellPrices);
        buyPrices = loadedBuyPrices;
        sellPrices = loadedSellPrices;
    }

    private void loadPriceSection(String path, Map<Material, Price> destination) {
        ConfigurationSection section = prices.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            double price = section.getDouble(materialName + ".price");
            int amount = section.getInt(materialName + ".amount");
            if (material == null || !material.isItem() || material.isAir()) {
                getLogger().warning("Ignoring invalid " + path.toLowerCase(Locale.ROOT)
                        + " material in prices.db: " + materialName);
            } else if (!Double.isFinite(price) || price <= 0.0D || amount <= 0) {
                getLogger().warning("Ignoring invalid " + path.toLowerCase(Locale.ROOT)
                        + " price for " + materialName);
            } else {
                destination.put(material, new Price(price, amount));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("shop-gui") || name.equals("buy-gui") || name.equals("sell-gui")) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return true;
            }
            if (name.equals("shop-gui")) {
                guiManager.openMain(player);
            } else if (name.equals("buy-gui")) {
                guiManager.openBuyCategories(player);
            } else {
                guiManager.openSell(player);
            }
            return true;
        }

        if (name.equals("commandshop")) {
            if (args.length == 0) {
                Player player = requirePlayer(sender);
                if (player != null) {
                    guiManager.openMain(player);
                }
            } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("commandshop.admin")) {
                    send(sender, "Error_NoPermission", Map.of());
                } else {
                    scheduleRuntimeReload(sender);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                handleRemovePriceCommand(sender, args[1], true, true);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
                inspect(sender, args[1]);
            } else {
                sender.sendMessage(color(
                        "&e/commandshop <reload|delete <item>|inspect <username>>"));
            }
            return true;
        }

        if (name.equals("shop")) {
            if (args.length == 0) {
                Player player = requirePlayer(sender);
                if (player != null) {
                    guiManager.openMain(player);
                }
            } else if (args.length == 2
                    && (args[0].equalsIgnoreCase("check")
                    || args[0].equalsIgnoreCase("inspect"))) {
                inspect(sender, args[1]);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                handleRemovePriceCommand(sender, args[1], true, true);
            } else {
                sender.sendMessage(color("&e/shop [inspect <username>|remove <item>]"));
            }
            return true;
        }

        if (name.equals("buy")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                handleRemovePriceCommand(sender, args[1], true, false);
            } else {
                handleBuyCommand(sender, args);
            }
            return true;
        }
        if (name.equals("sell")) {
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                handleRemovePriceCommand(sender, args[1], false, true);
            } else {
                handleSellCommand(sender, args);
            }
            return true;
        }
        if (name.equals("price")) {
            handlePriceCommand(sender, args);
            return true;
        }
        if (name.equals("setprice")) {
            handleSetPriceCommand(sender, args);
            return true;
        }
        return false;
    }

    private void scheduleRuntimeReload(CommandSender sender) {
        getServer().getGlobalRegionScheduler().execute(this, () -> {
            try {
                reloadConfig();
                getConfig().options().copyDefaults(true);

                prices = loadYaml(pricesFile);
                messages = loadMessages();
                loadAliases();
                loadPrices();

                if (guiManager != null) {
                    HandlerList.unregisterAll(guiManager);
                }
                guiManager = new ShopGuiManager(this);
                getServer().getPluginManager().registerEvents(guiManager, this);

                sendReloadResult(sender, true, null);
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not reload CommandShop runtime state.", exception);
                sendReloadResult(sender, false, exception.getClass().getSimpleName());
            }
        });
    }

    private void sendReloadResult(CommandSender sender, boolean success, String errorType) {
        Runnable response = () -> {
            if (success) {
                send(sender, "Reload_Success", Map.of(
                        "buy_count", Integer.toString(buyPrices.size()),
                        "sell_count", Integer.toString(sellPrices.size())));
            } else {
                send(sender, "Reload_Failed", Map.of(
                        "error", errorType == null ? "unknown error" : errorType));
            }
        };

        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.getScheduler().execute(this, response, null, 1L);
        } else {
            response.run();
        }
    }

    private void handleBuyCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            guiManager.openBuyCategories(player);
            return;
        }
        Material material = resolveMaterial(args[0], player);
        if (material == null) {
            send(player, "Error_UnknownItem", Map.of());
            return;
        }
        Price price = buyPrices.get(material);
        if (price == null) {
            send(player, "Error_NotBuyable", Map.of());
            return;
        }
        int amount = price.amount();
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("max")) {
                int capacity = inventoryCapacity(player, material);
                int affordableBundles = (int) Math.floor(economy.getBalance(player) / price.price());
                int capacityBundles = capacity / price.amount();
                amount = Math.min(affordableBundles, capacityBundles) * price.amount();
                if (amount <= 0) {
                    send(player, "Error_NotEnoughMoney", Map.of("price", formatMoney(price.price())));
                    return;
                }
            } else {
                Integer parsed = parsePositiveInt(args[1]);
                if (parsed == null) {
                    send(player, "Error_Number", Map.of());
                    return;
                }
                amount = parsed;
            }
        }
        purchase(player, material, amount);
    }

    private void handleSellCommand(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            guiManager.openSell(player);
            return;
        }
        Material material = resolveMaterial(args[0], player);
        if (material == null) {
            send(player, "Error_UnknownItem", Map.of());
            return;
        }
        Price price = sellPrices.get(material);
        if (price == null) {
            send(player, "Error_NotSellable", Map.of());
            return;
        }
        int amount = price.amount();
        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("max")) {
                amount = (countPlainMaterial(player, material) / price.amount()) * price.amount();
                if (amount <= 0) {
                    send(player, "Error_NotEnoughItems", Map.of());
                    return;
                }
            } else {
                Integer parsed = parsePositiveInt(args[1]);
                if (parsed == null) {
                    send(player, "Error_Number", Map.of());
                    return;
                }
                amount = parsed;
            }
        }
        if (amount % price.amount() != 0) {
            send(player, "Error_BundleSell", Map.of(
                    "item", displayName(material),
                    "bundle", Integer.toString(price.amount())));
            return;
        }
        executeSaleFromInventory(player, new SellQuote(
                Map.of(material, amount), price.price() * (amount / price.amount()), amount));
    }

    private void handlePriceCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&e/price <item|hand> [amount]"));
            return;
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        Material material = resolveMaterial(args[0], player);
        if (material == null) {
            send(sender, "Error_UnknownItem", Map.of());
            return;
        }
        int amount = 1;
        if (args.length >= 2) {
            Integer parsed = parsePositiveInt(args[1]);
            if (parsed == null) {
                send(sender, "Error_Number", Map.of());
                return;
            }
            amount = parsed;
        }
        boolean shown = false;
        Price buy = buyPrices.get(material);
        if (buy != null) {
            send(sender, "Price_Buy", Map.of(
                    "amount", Integer.toString(amount),
                    "item", displayName(material),
                    "price", formatMoney(buy.price() * amount / buy.amount()),
                    "bundle", buy.amount() + " for " + formatMoney(buy.price())));
            shown = true;
        }
        Price sell = sellPrices.get(material);
        if (sell != null) {
            send(sender, "Price_Sell", Map.of(
                    "amount", Integer.toString(amount),
                    "item", displayName(material),
                    "price", formatMoney(sell.price() * amount / sell.amount()),
                    "bundle", sell.amount() + " for " + formatMoney(sell.price())));
            shown = true;
        }
        if (!shown) {
            send(sender, "Price_None", Map.of());
        }
    }

    private void handleSetPriceCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("commandshop.admin")) {
            send(sender, "Error_NoPermission", Map.of());
            return;
        }
        if (args.length < 3 || (!args[0].equalsIgnoreCase("buy") && !args[0].equalsIgnoreCase("sell"))) {
            sender.sendMessage(color("&e/setprice <buy|sell> <item|hand> <price> [amount]"));
            return;
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        Material material = resolveMaterial(args[1], player);
        if (material == null || !material.isItem() || material.isAir()) {
            send(sender, "Error_UnknownItem", Map.of());
            return;
        }
        double value;
        int amount = 1;
        try {
            value = Double.parseDouble(args[2]);
            if (args.length >= 4) {
                amount = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException exception) {
            send(sender, "Error_Number", Map.of());
            return;
        }
        if (!Double.isFinite(value) || value < 0.0D || amount <= 0) {
            send(sender, "Error_Number", Map.of());
            return;
        }
        String type = args[0].equalsIgnoreCase("buy") ? "Buy" : "Sell";
        Map<Material, Price> destination = type.equals("Buy") ? buyPrices : sellPrices;
        removeExistingPriceEntry(type, material);
        if (value == 0.0D) {
            destination.remove(material);
            savePrices();
            send(sender, "SetPrice_Removed", Map.of(
                    "type", type.toLowerCase(Locale.ROOT),
                    "item", displayName(material)));
            return;
        }
        prices.set(type + "." + material.name() + ".price", value);
        prices.set(type + "." + material.name() + ".amount", amount);
        destination.put(material, new Price(value, amount));
        savePrices();
        send(sender, "SetPrice_Success", Map.of(
                "type", type.toLowerCase(Locale.ROOT),
                "item", displayName(material),
                "price", formatMoney(value),
                "amount", Integer.toString(amount)));
    }

    private void handleRemovePriceCommand(CommandSender sender, String materialName,
            boolean removeBuy, boolean removeSell) {
        if (!sender.hasPermission("commandshop.admin")) {
            send(sender, "Error_NoPermission", Map.of());
            return;
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        Material material = resolveMaterial(materialName, player);
        if (material == null || !material.isItem() || material.isAir()) {
            send(sender, "Error_UnknownItem", Map.of());
            return;
        }

        boolean removedBuy = false;
        boolean removedSell = false;
        if (removeBuy) {
            removedBuy = removeExistingPriceEntry("Buy", material)
                    | buyPrices.remove(material) != null;
        }
        if (removeSell) {
            removedSell = removeExistingPriceEntry("Sell", material)
                    | sellPrices.remove(material) != null;
        }
        boolean twoSidedRemoval = removeBuy && removeSell;
        if (!removedBuy && !removedSell && !twoSidedRemoval) {
            send(sender, "Remove_None", Map.of("item", displayName(material)));
            return;
        }

        if (removedBuy || removedSell) {
            savePrices();
        }
        String messageKey;
        if (twoSidedRemoval) {
            messageKey = "Remove_ShopBoth";
        } else if (removeBuy) {
            messageKey = "Remove_Buy";
        } else {
            messageKey = "Remove_Sell";
        }
        send(sender, messageKey, Map.of("item", displayName(material)));
    }

    private boolean removeExistingPriceEntry(String type, Material material) {
        ConfigurationSection section = prices.getConfigurationSection(type);
        if (section == null) {
            return false;
        }
        boolean removed = false;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            if (Material.matchMaterial(key) == material) {
                prices.set(type + "." + key, null);
                removed = true;
            }
        }
        return removed;
    }

    private void savePrices() {
        try {
            prices.save(pricesFile);
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not save prices.db", exception);
        }
    }

    public boolean purchase(Player player, Material material, int amount) {
        Price price = buyPrices.get(material);
        if (price == null) {
            send(player, "Error_NotBuyable", Map.of());
            return false;
        }
        if (amount <= 0 || amount % price.amount() != 0) {
            send(player, "Error_BundleBuy", Map.of(
                    "item", displayName(material),
                    "bundle", Integer.toString(price.amount())));
            return false;
        }
        double total = price.price() * (amount / price.amount());
        if (economy.getBalance(player) + 0.0000001D < total) {
            send(player, "Error_NotEnoughMoney", Map.of("price", formatMoney(total)));
            return false;
        }
        if (inventoryCapacity(player, material) < amount) {
            send(player, "Error_NoSpace", Map.of());
            return false;
        }

        ItemStack[] before = cloneContents(player.getInventory().getStorageContents());
        EconomyResponse response = economy.withdrawPlayer(player, total);
        if (!response.transactionSuccess()) {
            send(player, "Error_Economy", Map.of());
            return false;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, amount));
        if (!leftovers.isEmpty()) {
            player.getInventory().setStorageContents(before);
            economy.depositPlayer(player, total);
            send(player, "Error_NoSpace", Map.of());
            return false;
        }

        recordTransaction(player, "Buy", material, amount, total);
        send(player, "Buy_Success", Map.of(
                "amount", Integer.toString(amount),
                "item", displayName(material),
                "price", formatMoney(total)));
        return true;
    }

    public SellQuote quoteSellable(Iterable<ItemStack> stacks) {
        Map<Material, Integer> found = new HashMap<>();
        for (ItemStack stack : stacks) {
            if (!isPlainSellItem(stack) || !sellPrices.containsKey(stack.getType())) {
                continue;
            }
            found.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        Map<Material, Integer> sellable = new LinkedHashMap<>();
        double total = 0.0D;
        int totalItems = 0;
        List<Material> sorted = new ArrayList<>(found.keySet());
        sorted.sort(Comparator.comparing(Material::name));
        for (Material material : sorted) {
            Price price = sellPrices.get(material);
            int amount = (found.get(material) / price.amount()) * price.amount();
            if (amount <= 0) {
                continue;
            }
            sellable.put(material, amount);
            total += price.price() * (amount / price.amount());
            totalItems += amount;
        }
        return new SellQuote(Collections.unmodifiableMap(sellable), total, totalItems);
    }

    public SellQuote quoteSellable(ItemStack[] stacks) {
        List<ItemStack> items = new ArrayList<>();
        Collections.addAll(items, stacks);
        return quoteSellable(items);
    }

    public boolean executeSaleFromInventory(Player player, SellQuote quote) {
        if (quote == null || quote.amounts().isEmpty()) {
            send(player, "Sell_Nothing", Map.of());
            return false;
        }
        for (Map.Entry<Material, Integer> entry : quote.amounts().entrySet()) {
            if (countPlainMaterial(player, entry.getKey()) < entry.getValue()) {
                send(player, "Error_NotEnoughItems", Map.of());
                return false;
            }
        }

        ItemStack[] before = cloneContents(player.getInventory().getStorageContents());
        for (Map.Entry<Material, Integer> entry : quote.amounts().entrySet()) {
            removePlainMaterial(player, entry.getKey(), entry.getValue());
        }
        EconomyResponse response = economy.depositPlayer(player, quote.total());
        if (!response.transactionSuccess()) {
            player.getInventory().setStorageContents(before);
            send(player, "Error_Economy", Map.of());
            return false;
        }
        for (Map.Entry<Material, Integer> entry : quote.amounts().entrySet()) {
            Price price = sellPrices.get(entry.getKey());
            double earned = price.price() * (entry.getValue() / price.amount());
            recordTransaction(player, "Sell", entry.getKey(), entry.getValue(), earned);
        }
        String soldItemName = quote.amounts().size() == 1
                ? displayName(quote.amounts().keySet().iterator().next())
                : "Mixed Items";
        send(player, "Sell_Success", Map.of(
                "amount", Integer.toString(quote.totalItems()),
                "item", soldItemName,
                "price", formatMoney(quote.total())));
        return true;
    }

    private boolean isPlainSellItem(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0 && !stack.hasItemMeta();
    }

    private int countPlainMaterial(Player player, Material material) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isPlainSellItem(stack) && stack.getType() == material) {
                found += stack.getAmount();
            }
        }
        return found;
    }

    private void removePlainMaterial(Player player, Material material, int requested) {
        int remaining = requested;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!isPlainSellItem(stack) || stack.getType() != material) {
                continue;
            }
            int removed = Math.min(stack.getAmount(), remaining);
            remaining -= removed;
            if (removed == stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - removed);
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private int inventoryCapacity(Player player, Material material) {
        ItemStack example = new ItemStack(material);
        int max = example.getMaxStackSize();
        int capacity = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                capacity += max;
            } else if (stack.isSimilar(example)) {
                capacity += Math.max(0, max - stack.getAmount());
            }
        }
        return capacity;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private void recordTransaction(Player player, String type, Material material, int amount, double money) {
        String base = "Players." + player.getUniqueId();
        String path = base + "." + type + "." + material.name();
        synchronized (statsLock) {
            stats.set(base + ".Name", player.getName());
            stats.set(path + ".amount", stats.getLong(path + ".amount") + amount);
            stats.set(path + ".money", stats.getDouble(path + ".money") + money);
            if (type.equals("Buy")) {
                List<String> recent = new ArrayList<>(stats.getStringList(base + ".RecentPurchases"));
                recent.remove(material.name());
                recent.add(0, material.name());
                int limit = Math.max(1, getConfig().getInt("Recent_Purchase_Limit", 45));
                if (recent.size() > limit) {
                    recent = new ArrayList<>(recent.subList(0, limit));
                }
                stats.set(base + ".RecentPurchases", recent);
            }
        }
        queueStatsSave();
    }

    private void queueStatsSave() {
        if (stats == null || statsFile == null || statsWriter == null
                || (shuttingDown && statsWriter.isShutdown())) {
            return;
        }
        final String snapshot;
        synchronized (statsLock) {
            snapshot = stats.saveToString();
        }
        try {
            statsWriter.execute(() -> {
                File temporary = new File(statsFile.getParentFile(), statsFile.getName() + ".tmp");
                try {
                    Files.writeString(temporary.toPath(), snapshot, StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary.toPath(), statsFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException atomicMoveFailure) {
                        Files.move(temporary.toPath(), statsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    getLogger().log(Level.SEVERE, "Could not save stats.db", exception);
                }
            });
        } catch (RuntimeException exception) {
            if (!shuttingDown) {
                getLogger().log(Level.SEVERE, "Could not queue stats.db save", exception);
            }
        }
    }

    private void inspect(CommandSender sender, String requestedName) {
        if (!sender.hasPermission("commandshop.inspect")) {
            send(sender, "Error_NoPermission", Map.of());
            return;
        }
        String playerKey = findPlayerKey(requestedName);
        if (playerKey == null) {
            send(sender, "Inspect_None", Map.of());
            return;
        }
        String display = requestedName;
        synchronized (statsLock) {
            display = stats.getString("Players." + playerKey + ".Name", requestedName);
        }
        send(sender, "Inspect_Header", Map.of("player", display));
        send(sender, "Inspect_SellHeader", Map.of());
        List<ShopStat> sold = topStats(playerKey, "Sell", 5);
        if (sold.isEmpty()) {
            send(sender, "Inspect_None", Map.of());
        } else {
            sendStatLines(sender, sold, false);
        }
        send(sender, "Inspect_BuyHeader", Map.of());
        List<ShopStat> bought = topStats(playerKey, "Buy", 5);
        if (bought.isEmpty()) {
            send(sender, "Inspect_None", Map.of());
        } else {
            sendStatLines(sender, bought, true);
        }
    }

    private void sendStatLines(CommandSender sender, List<ShopStat> entries, boolean bought) {
        for (int index = 0; index < entries.size(); index++) {
            ShopStat entry = entries.get(index);
            send(sender, bought ? "Inspect_LineBuy" : "Inspect_LineSell", Map.of(
                    "rank", Integer.toString(index + 1),
                    "item", displayName(entry.material()),
                    "amount", Long.toString(entry.amount()),
                    "money", formatMoney(entry.money())));
        }
    }

    private String findPlayerKey(String requestedName) {
        synchronized (statsLock) {
            ConfigurationSection players = stats.getConfigurationSection("Players");
            if (players == null) {
                return null;
            }
            for (String key : players.getKeys(false)) {
                if (requestedName.equalsIgnoreCase(stats.getString("Players." + key + ".Name", ""))) {
                    return key;
                }
            }
        }
        return null;
    }

    private List<ShopStat> topStats(String playerKey, String type, int limit) {
        List<ShopStat> result = new ArrayList<>();
        synchronized (statsLock) {
            ConfigurationSection section = stats.getConfigurationSection(
                    "Players." + playerKey + "." + type);
            if (section == null) {
                return result;
            }
            for (String materialName : section.getKeys(false)) {
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    continue;
                }
                result.add(new ShopStat(
                        material,
                        section.getLong(materialName + ".amount"),
                        section.getDouble(materialName + ".money")));
            }
        }
        result.sort(Comparator.comparingDouble(ShopStat::money).reversed()
                .thenComparing(Comparator.comparingLong(ShopStat::amount).reversed())
                .thenComparing(entry -> entry.material().name()));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    public List<ShopStat> getTopStats(OfflinePlayer player, String type, int limit) {
        if (player == null) {
            return List.of();
        }
        String key = player.getUniqueId().toString();
        synchronized (statsLock) {
            if (!stats.isConfigurationSection("Players." + key) && player.getName() != null) {
                key = findPlayerKey(player.getName());
            }
        }
        if (key == null) {
            return List.of();
        }
        return topStats(key, type.equalsIgnoreCase("sell") ? "Sell" : "Buy", limit);
    }

    public double getStatTotal(OfflinePlayer player, String type, String field) {
        double total = 0.0D;
        for (ShopStat stat : getTopStats(player, type, Integer.MAX_VALUE)) {
            total += field.equalsIgnoreCase("money") ? stat.money() : stat.amount();
        }
        return total;
    }

    public List<Material> getRecentPurchases(UUID playerId) {
        List<Material> result = new ArrayList<>();
        synchronized (statsLock) {
            for (String materialName : stats.getStringList(
                    "Players." + playerId + ".RecentPurchases")) {
                Material material = Material.matchMaterial(materialName);
                if (material != null && buyPrices.containsKey(material)) {
                    result.add(material);
                }
            }
        }
        return result;
    }

    public List<String> getKnownPlayerNames() {
        List<String> result = new ArrayList<>();
        synchronized (statsLock) {
            ConfigurationSection players = stats.getConfigurationSection("Players");
            if (players != null) {
                for (String key : players.getKeys(false)) {
                    String name = stats.getString("Players." + key + ".Name");
                    if (name != null && !name.isBlank()) {
                        result.add(name);
                    }
                }
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public Material resolveMaterial(String input, Player player) {
        if (input == null) {
            return null;
        }
        if (input.equalsIgnoreCase("hand")) {
            if (player == null || player.getInventory().getItemInMainHand().getType().isAir()) {
                return null;
            }
            return player.getInventory().getItemInMainHand().getType();
        }
        return aliases.get(input.toLowerCase(Locale.ROOT));
    }

    public Map<Material, Price> getBuyPrices() {
        return Collections.unmodifiableMap(buyPrices);
    }

    public Map<Material, Price> getSellPrices() {
        return Collections.unmodifiableMap(sellPrices);
    }

    public List<Material> getBuyableMaterials() {
        return sortedMaterials(buyPrices.keySet());
    }

    public List<Material> getSellableMaterials() {
        return sortedMaterials(sellPrices.keySet());
    }

    private List<Material> sortedMaterials(Iterable<Material> materials) {
        List<Material> result = new ArrayList<>();
        materials.forEach(result::add);
        result.sort(Comparator.comparing(Material::name));
        return Collections.unmodifiableList(result);
    }

    public Price getBuyPrice(Material material) {
        return buyPrices.get(material);
    }

    public Price getSellPrice(Material material) {
        return sellPrices.get(material);
    }

    public double getBalance(Player player) {
        return economy.getBalance(player);
    }

    public String formatMoney(double value) {
        String formatted;
        synchronized (moneyFormat) {
            formatted = moneyFormat.format(value);
        }
        return "$" + formatted;
    }

    public String displayName(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        String value = messages.getString(key, key);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            String replacementKey = replacement.getKey();
            String replacementValue = replacement.getValue();
            value = value.replace("%" + replacementKey + "%", replacementValue);
            value = value.replace(
                    "%scoreboardchatshop_" + replacementKey + "%", replacementValue);

            if (replacementKey.equals("item")) {
                value = value.replace(
                        "%scoreboardchatshop_material%", replacementValue);
                value = value.replace(
                        "%scoreboardchatshop_basematerial%", replacementValue);
            }
        }
        value = normalizeLegacyMessageFormatting(value);
        if (sender instanceof Player && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            value = PlaceholderAPI.setPlaceholders((Player) sender, value);
        }
        sender.sendMessage(color(messages.getString("Prefix", "") + value));
    }

    private String normalizeLegacyMessageFormatting(String value) {
        value = value.replaceAll(
                "&[><](?:LCH|RGB)#[0-9A-Fa-f]{6}", "");
        value = value.replace("&??", "").replace("&?", "");
        while (value.contains("$$")) {
            value = value.replace("$$", "$");
        }
        return value;
    }

    public String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            send(sender, "Error_PlayerOnly", Map.of());
            return null;
        }
        if (!sender.hasPermission("commandshop.use")) {
            send(sender, "Error_NoPermission", Map.of());
            return null;
        }
        return (Player) sender;
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
