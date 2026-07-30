package me.thetealviper.commandshop.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.thetealviper.commandshop.api.CommandShopApi;
import me.thetealviper.commandshop.model.Price;
import me.thetealviper.commandshop.model.SellQuote;
import me.thetealviper.commandshop.shop.ItemCategoryClassifier;

public final class ShopGuiManager implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int[] BUNDLE_SLOTS = {20, 21, 22, 23, 24};

    private final CommandShopApi plugin;
    private final List<GroupDefinition> materialGroups = new ArrayList<>();
    private final Map<BuyCategory, Set<Material>> overrides = new EnumMap<>(BuyCategory.class);

    public ShopGuiManager(CommandShopApi plugin) {
        this.plugin = plugin;
        loadCategoryOverrides();
        loadMaterialGroups();
    }

    public void openMain(Player player) {
        MainHolder holder = new MainHolder();
        holder.inventory.setItem(11, item(Material.EMERALD,
                "&a&lBuy Items",
                "&7Browse organized categories.",
                "&7Only available items are displayed.",
                "",
                "&eClick to browse"));
        holder.inventory.setItem(13, item(Material.NETHER_STAR,
                "&6&lCommandShop",
                "&7Balance: &f" + plugin.formatMoney(plugin.getBalance(player)),
                "",
                "&7Use &f/buy-gui&7, &f/sell-gui",
                "&7or &f/shop-gui &7for direct access."));
        holder.inventory.setItem(15, item(Material.HOPPER,
                "&e&lSell Items",
                "&7Drag items into a safe sell tray",
                "&7or sell every eligible inventory item.",
                "",
                "&eClick to sell"));
        player.openInventory(holder.inventory);
    }

    public void openBuyCategories(Player player) {
        BuyCategoriesHolder holder = new BuyCategoriesHolder();
        int[] slots = {10, 11, 12, 13, 14};
        BuyCategory[] categories = BuyCategory.values();
        for (int index = 0; index < categories.length; index++) {
            BuyCategory category = categories[index];
            int count = materialsFor(player, category).size();
            holder.inventory.setItem(slots[index], item(category.icon, category.display,
                    "&7" + count + " available item" + (count == 1 ? "" : "s"),
                    category.description,
                    "",
                    count == 0 ? "&8Nothing is available here yet." : "&eClick to browse"));
        }
        holder.inventory.setItem(22, item(Material.ARROW, "&cBack", "&7Return to the shop menu."));
        player.openInventory(holder.inventory);
    }

    public void openSell(Player player) {
        SellInputHolder holder = new SellInputHolder();
        holder.inventory.setItem(45, item(Material.ARROW, "&cBack", "&7Return to the shop menu."));
        holder.inventory.setItem(47, item(Material.BOOK, "&6How to sell",
                "&71. Drag items into the upper tray.",
                "&72. Click Review sale.",
                "&73. Confirm the displayed total.",
                "",
                "&8Custom, named, enchanted, or damaged",
                "&8items are never sold."));
        holder.inventory.setItem(49, item(Material.GOLD_INGOT, "&a&lReview sale",
                "&7Review eligible full bundles",
                "&7from the items in this tray."));
        holder.inventory.setItem(53, item(Material.CHEST, "&e&lSell all inventory",
                "&7Review every eligible full bundle",
                "&7in your survival inventory."));
        player.openInventory(holder.inventory);
    }

    private void openCategory(Player player, BuyCategory category, int requestedPage) {
        List<BuyEntry> entries = buildEntries(player, category);
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        CategoryHolder holder = new CategoryHolder(category, page, entries);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < entries.size(); slot++) {
            BuyEntry entry = entries.get(start + slot);
            List<String> lore = new ArrayList<>();
            if (entry.materials.size() > 1) {
                lore.add("&7" + entry.materials.size() + " available variants");
                lore.add("");
                lore.add("&eClick to choose a variant");
            } else {
                Price price = plugin.getBuyPrice(entry.materials.get(0));
                lore.add("&7Base bundle: &f" + price.amount());
                lore.add("&7Price: &f" + plugin.formatMoney(price.price()));
                lore.add("");
                lore.add("&eClick for bundle prices");
            }
            holder.inventory.setItem(slot, item(entry.icon, "&f" + entry.display,
                    lore.toArray(new String[0])));
        }
        if (entries.isEmpty()) {
            holder.inventory.setItem(22, item(Material.BARRIER, "&cNo available items",
                    "&7This section only appears when items",
                    "&7have an active buy price."));
        }
        setPageNavigation(holder.inventory, page, maxPage, category.display);
        player.openInventory(holder.inventory);
    }

    private void openVariants(Player player, BuyCategory category, int categoryPage,
            BuyEntry entry, int requestedVariantPage) {
        int maxPage = Pagination.maxPage(entry.materials.size(), PAGE_SIZE);
        int variantPage = Pagination.clampPage(
                requestedVariantPage, entry.materials.size(), PAGE_SIZE);
        VariantsHolder holder = new VariantsHolder(
                category, categoryPage, entry, variantPage);
        int start = variantPage * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < entry.materials.size(); slot++) {
            Material material = entry.materials.get(start + slot);
            Price price = plugin.getBuyPrice(material);
            holder.inventory.setItem(slot, item(material, "&f" + plugin.displayName(material),
                    "&7Base bundle: &f" + price.amount(),
                    "&7Price: &f" + plugin.formatMoney(price.price()),
                    "",
                    "&eClick for bundle prices"));
        }
        holder.inventory.setItem(45, item(Material.ARROW, "&cBack", "&7Return to " + entry.display + "."));
        if (variantPage > 0) {
            holder.inventory.setItem(48, item(Material.ARROW, "&ePrevious page"));
        }
        holder.inventory.setItem(49, item(entry.icon, "&6" + entry.display,
                "&7Page &f" + (variantPage + 1) + "&7/&f" + (maxPage + 1),
                "&7All variants shown here are available via &f/buy&7."));
        if (variantPage < maxPage) {
            holder.inventory.setItem(50, item(Material.ARROW, "&eNext page"));
        }
        player.openInventory(holder.inventory);
    }

    private void openDetails(Player player, BuyCategory category, int categoryPage,
            BuyEntry parentEntry, int variantPage, Material material) {
        Price price = plugin.getBuyPrice(material);
        if (price == null) {
            plugin.send(player, "Error_NotBuyable", Map.of());
            openCategory(player, category, categoryPage);
            return;
        }
        DetailHolder holder = new DetailHolder(
                category, categoryPage, parentEntry, variantPage, material);
        double unitPrice = price.price() / price.amount();
        holder.inventory.setItem(13, item(material, "&6&l" + plugin.displayName(material),
                "&7Price per item: &f" + plugin.formatMoney(unitPrice),
                "&7Base bundle: &f" + price.amount() + " for " + plugin.formatMoney(price.price()),
                "&7Your balance: &f" + plugin.formatMoney(plugin.getBalance(player)),
                "",
                "&7Choose an available bundle below."));
        for (int index = 0; index < BUNDLE_SLOTS.length; index++) {
            int multiplier = index + 1;
            int amount = price.amount() * multiplier;
            double total = price.price() * multiplier;
            ItemStack button = item(material, "&aBuy " + amount,
                    "&7Price: &f" + plugin.formatMoney(total),
                    "&7Bundle: &f" + multiplier + " x " + price.amount(),
                    "",
                    "&eClick to purchase");
            button.setAmount(Math.max(1, Math.min(64, amount)));
            holder.inventory.setItem(BUNDLE_SLOTS[index], button);
        }
        holder.inventory.setItem(36, item(Material.ARROW, "&cBack", "&7Return to the item list."));
        holder.inventory.setItem(40, item(Material.CHEST, "&6Categories", "&7Return to buy categories."));
        player.openInventory(holder.inventory);
    }

    private void openSellConfirmation(Player player, SellQuote quote, String source) {
        SellConfirmHolder holder = new SellConfirmHolder(quote);
        List<String> summary = new ArrayList<>();
        summary.add("&7Source: &f" + source);
        summary.add("&7Items sold: &f" + quote.totalItems());
        summary.add("&7You receive: &a" + plugin.formatMoney(quote.total()));
        summary.add("");
        int shown = 0;
        for (Map.Entry<Material, Integer> entry : quote.amounts().entrySet()) {
            if (shown++ == 8) {
                summary.add("&8...and " + (quote.amounts().size() - 8) + " more");
                break;
            }
            summary.add("&8• &f" + entry.getValue() + "x " + plugin.displayName(entry.getKey()));
        }
        holder.inventory.setItem(13, item(Material.GOLD_INGOT, "&6&lSale total",
                summary.toArray(new String[0])));
        holder.inventory.setItem(11, item(Material.RED_WOOL, "&cCancel",
                "&7Keep all selected items."));
        holder.inventory.setItem(15, item(Material.LIME_WOOL, "&a&lConfirm sale",
                "&7Remove the listed items and receive",
                "&f" + plugin.formatMoney(quote.total()) + "&7."));
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder rawHolder = top.getHolder();
        if (!(rawHolder instanceof ShopHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (rawHolder instanceof SellInputHolder) {
            handleSellInputClick(event, player, top);
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        if (rawHolder instanceof MainHolder) {
            if (slot == 11) {
                openBuyCategories(player);
            } else if (slot == 15) {
                openSell(player);
            }
        } else if (rawHolder instanceof BuyCategoriesHolder) {
            if (slot == 22) {
                openMain(player);
            } else {
                int index = Arrays.asList(10, 11, 12, 13, 14).indexOf(slot);
                if (index >= 0) {
                    openCategory(player, BuyCategory.values()[index], 0);
                }
            }
        } else if (rawHolder instanceof CategoryHolder) {
            handleCategoryClick(player, (CategoryHolder) rawHolder, slot);
        } else if (rawHolder instanceof VariantsHolder) {
            VariantsHolder holder = (VariantsHolder) rawHolder;
            if (slot == 45) {
                openCategory(player, holder.category, holder.categoryPage);
            } else if (slot == 48 && holder.variantPage > 0) {
                openVariants(player, holder.category, holder.categoryPage,
                        holder.entry, holder.variantPage - 1);
            } else if (slot == 50 && holder.variantPage < Pagination.maxPage(
                    holder.entry.materials.size(), PAGE_SIZE)) {
                openVariants(player, holder.category, holder.categoryPage,
                        holder.entry, holder.variantPage + 1);
            } else if (slot >= 0 && slot < PAGE_SIZE) {
                int index = Pagination.index(holder.variantPage, slot, PAGE_SIZE);
                if (index < 0 || index >= holder.entry.materials.size()) {
                    return;
                }
                openDetails(player, holder.category, holder.categoryPage,
                        holder.entry, holder.variantPage, holder.entry.materials.get(index));
            }
        } else if (rawHolder instanceof DetailHolder) {
            handleDetailClick(player, (DetailHolder) rawHolder, slot);
        } else if (rawHolder instanceof SellConfirmHolder) {
            SellConfirmHolder holder = (SellConfirmHolder) rawHolder;
            if (slot == 11) {
                openSell(player);
            } else if (slot == 15 && plugin.executeSaleFromInventory(player, holder.quote)) {
                player.closeInventory();
            }
        }
    }

    private void handleCategoryClick(Player player, CategoryHolder holder, int slot) {
        int maxPage = Math.max(0, (holder.entries.size() - 1) / PAGE_SIZE);
        if (slot == 45) {
            openBuyCategories(player);
            return;
        }
        if (slot == 48 && holder.page > 0) {
            openCategory(player, holder.category, holder.page - 1);
            return;
        }
        if (slot == 50 && holder.page < maxPage) {
            openCategory(player, holder.category, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        int index = holder.page * PAGE_SIZE + slot;
        if (index >= holder.entries.size()) {
            return;
        }
        BuyEntry entry = holder.entries.get(index);
        if (entry.materials.size() > 1) {
            openVariants(player, holder.category, holder.page, entry, 0);
        } else {
            openDetails(player, holder.category, holder.page, null, 0, entry.materials.get(0));
        }
    }

    private void handleDetailClick(Player player, DetailHolder holder, int slot) {
        if (slot == 36) {
            if (holder.parentEntry == null) {
                openCategory(player, holder.category, holder.categoryPage);
            } else {
                openVariants(player, holder.category, holder.categoryPage,
                        holder.parentEntry, holder.variantPage);
            }
            return;
        }
        if (slot == 40) {
            openBuyCategories(player);
            return;
        }
        for (int index = 0; index < BUNDLE_SLOTS.length; index++) {
            if (slot != BUNDLE_SLOTS[index]) {
                continue;
            }
            Price price = plugin.getBuyPrice(holder.material);
            if (price != null && plugin.purchase(player, holder.material, price.amount() * (index + 1))) {
                openDetails(player, holder.category, holder.categoryPage,
                        holder.parentEntry, holder.variantPage, holder.material);
            }
            return;
        }
    }

    private void handleSellInputClick(InventoryClickEvent event, Player player, Inventory top) {
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < 45) {
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot >= 45 && rawSlot < 54) {
            event.setCancelled(true);
            if (rawSlot == 45) {
                openMain(player);
            } else if (rawSlot == 49) {
                List<ItemStack> deposited = takeSellInput(top);
                returnItems(player, deposited);
                SellQuote quote = plugin.quoteSellable(deposited);
                if (quote.amounts().isEmpty()) {
                    plugin.send(player, "Sell_Nothing", Map.of());
                } else {
                    openSellConfirmation(player, quote, "Sell tray");
                }
            } else if (rawSlot == 53) {
                List<ItemStack> deposited = takeSellInput(top);
                returnItems(player, deposited);
                SellQuote quote = plugin.quoteSellable(player.getInventory().getStorageContents());
                if (quote.amounts().isEmpty()) {
                    plugin.send(player, "Sell_Nothing", Map.of());
                } else {
                    openSellConfirmation(player, quote, "Full inventory");
                }
            }
            return;
        }
        if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopHolder)) {
            return;
        }
        if (!(holder instanceof SellInputHolder)) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 45 && rawSlot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof SellInputHolder)
                || !(event.getPlayer() instanceof Player)) {
            return;
        }
        List<ItemStack> items = takeSellInput(inventory);
        if (!items.isEmpty()) {
            returnItems((Player) event.getPlayer(), items);
        }
    }

    private List<ItemStack> takeSellInput(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                result.add(stack.clone());
                inventory.setItem(slot, null);
            }
        }
        return result;
    }

    private void returnItems(Player player, Collection<ItemStack> items) {
        for (ItemStack stack : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private List<BuyEntry> buildEntries(Player player, BuyCategory category) {
        List<Material> materials = materialsFor(player, category);
        if (category != BuyCategory.MATERIALS) {
            List<BuyEntry> result = new ArrayList<>();
            for (Material material : materials) {
                result.add(BuyEntry.single(material, plugin.displayName(material)));
            }
            return result;
        }

        List<BuyEntry> result = new ArrayList<>();
        Set<Material> consumed = new HashSet<>();
        for (GroupDefinition group : materialGroups) {
            List<Material> matches = new ArrayList<>();
            for (Material material : materials) {
                if (!consumed.contains(material) && group.matches(material)) {
                    matches.add(material);
                }
            }
            if (!matches.isEmpty()) {
                matches.sort(Comparator.comparing(Material::name));
                consumed.addAll(matches);
                Material icon = group.icon != null && matches.contains(group.icon)
                        ? group.icon : matches.get(0);
                result.add(new BuyEntry(group.name, icon, matches));
            }
        }
        for (Material material : materials) {
            if (!consumed.contains(material)) {
                result.add(BuyEntry.single(material, plugin.displayName(material)));
            }
        }
        return result;
    }

    private List<Material> materialsFor(Player player, BuyCategory category) {
        if (category == BuyCategory.RECENT) {
            return plugin.getRecentPurchases(player.getUniqueId());
        }
        List<Material> result = new ArrayList<>();
        for (Material material : plugin.getBuyableMaterials()) {
            if (categoryOf(material) == category) {
                result.add(material);
            }
        }
        result.sort(Comparator.comparing(Material::name));
        return result;
    }

    private BuyCategory categoryOf(Material material) {
        for (BuyCategory category : List.of(
                BuyCategory.FOOD, BuyCategory.MATERIALS, BuyCategory.ORES, BuyCategory.OTHER)) {
            if (overrides.getOrDefault(category, Set.of()).contains(material)) {
                return category;
            }
        }
        return switch (ItemCategoryClassifier.classify(material)) {
            case FOOD -> BuyCategory.FOOD;
            case MATERIALS -> BuyCategory.MATERIALS;
            case ORES -> BuyCategory.ORES;
            case OTHER -> BuyCategory.OTHER;
        };
    }

    private void loadCategoryOverrides() {
        for (BuyCategory category : BuyCategory.values()) {
            overrides.put(category, new HashSet<>());
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("Category_Overrides");
        if (section == null) {
            return;
        }
        Map<String, BuyCategory> keys = Map.of(
                "Food_And_Crops", BuyCategory.FOOD,
                "Materials", BuyCategory.MATERIALS,
                "Ores", BuyCategory.ORES,
                "Other", BuyCategory.OTHER);
        for (Map.Entry<String, BuyCategory> entry : keys.entrySet()) {
            for (String materialName : section.getStringList(entry.getKey())) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    overrides.get(entry.getValue()).add(material);
                } else {
                    plugin.getLogger().warning("Unknown category override material: " + materialName);
                }
            }
        }
    }

    private void loadMaterialGroups() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("Material_Groups");
        if (section == null) {
            return;
        }
        for (String groupName : section.getKeys(false)) {
            Material icon = Material.matchMaterial(section.getString(groupName + ".icon", ""));
            List<String> patterns = new ArrayList<>();
            for (String pattern : section.getStringList(groupName + ".patterns")) {
                patterns.add(pattern.toUpperCase(Locale.ROOT));
            }
            if (!patterns.isEmpty()) {
                materialGroups.add(new GroupDefinition(groupName, icon, patterns));
            }
        }
    }

    private void setPageNavigation(Inventory inventory, int page, int maxPage, String title) {
        inventory.setItem(45, item(Material.ARROW, "&cBack", "&7Return to buy categories."));
        if (page > 0) {
            inventory.setItem(48, item(Material.ARROW, "&ePrevious page"));
        }
        inventory.setItem(49, item(Material.PAPER, "&6" + title,
                "&7Page &f" + (page + 1) + "&7/&f" + (maxPage + 1)));
        if (page < maxPage) {
            inventory.setItem(50, item(Material.ARROW, "&eNext page"));
        }
    }

    private ItemStack item(Material material, String displayName, String... loreLines) {
        ItemStack stack = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(displayName));
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(color(line));
                }
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private enum BuyCategory {
        FOOD("&aFood & Crops", Material.GOLDEN_CARROT,
                "&7Food, seeds, crops, and farm goods."),
        MATERIALS("&6Materials", Material.BRICKS,
                "&7Building blocks and construction variants."),
        ORES("&bOres", Material.IRON_PICKAXE,
                "&7Ores, mined minerals, clay, and storage blocks."),
        OTHER("&dOther", Material.CHEST,
                "&7Crafting stations, utility, gear, and other items."),
        RECENT("&eRecent Purchases", Material.CLOCK,
                "&7Your most recently purchased items.");

        private final String display;
        private final Material icon;
        private final String description;

        BuyCategory(String display, Material icon, String description) {
            this.display = display;
            this.icon = icon;
            this.description = description;
        }
    }

    private static final class GroupDefinition {
        private final String name;
        private final Material icon;
        private final List<String> patterns;

        private GroupDefinition(String name, Material icon, List<String> patterns) {
            this.name = name;
            this.icon = icon;
            this.patterns = patterns;
        }

        private boolean matches(Material material) {
            String name = material.name();
            for (String pattern : patterns) {
                if (pattern.startsWith("*") && name.endsWith(pattern.substring(1))) {
                    return true;
                }
                if (!pattern.contains("*") && name.equals(pattern)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class BuyEntry {
        private final String display;
        private final Material icon;
        private final List<Material> materials;

        private BuyEntry(String display, Material icon, List<Material> materials) {
            this.display = display;
            this.icon = icon;
            this.materials = Collections.unmodifiableList(new ArrayList<>(materials));
        }

        private static BuyEntry single(Material material, String display) {
            return new BuyEntry(display, material, List.of(material));
        }
    }

    private abstract static class ShopHolder implements InventoryHolder {
        protected final Inventory inventory;

        private ShopHolder(int size, String title) {
            inventory = org.bukkit.Bukkit.createInventory(this, size,
                    ChatColor.translateAlternateColorCodes('&', title));
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class MainHolder extends ShopHolder {
        private MainHolder() {
            super(27, "&8CommandShop");
        }
    }

    private static final class BuyCategoriesHolder extends ShopHolder {
        private BuyCategoriesHolder() {
            super(27, "&8Buy • Categories");
        }
    }

    private static final class CategoryHolder extends ShopHolder {
        private final BuyCategory category;
        private final int page;
        private final List<BuyEntry> entries;

        private CategoryHolder(BuyCategory category, int page, List<BuyEntry> entries) {
            super(54, "&8Buy • " + ChatColor.stripColor(
                    ChatColor.translateAlternateColorCodes('&', category.display)));
            this.category = category;
            this.page = page;
            this.entries = entries;
        }
    }

    private static final class VariantsHolder extends ShopHolder {
        private final BuyCategory category;
        private final int categoryPage;
        private final BuyEntry entry;
        private final int variantPage;

        private VariantsHolder(BuyCategory category, int categoryPage,
                BuyEntry entry, int variantPage) {
            super(54, "&8Choose • " + entry.display);
            this.category = category;
            this.categoryPage = categoryPage;
            this.entry = entry;
            this.variantPage = variantPage;
        }
    }

    private static final class DetailHolder extends ShopHolder {
        private final BuyCategory category;
        private final int categoryPage;
        private final BuyEntry parentEntry;
        private final int variantPage;
        private final Material material;

        private DetailHolder(BuyCategory category, int categoryPage,
                BuyEntry parentEntry, int variantPage, Material material) {
            super(45, "&8Buy • " + material.name());
            this.category = category;
            this.categoryPage = categoryPage;
            this.parentEntry = parentEntry;
            this.variantPage = variantPage;
            this.material = material;
        }
    }

    private static final class SellInputHolder extends ShopHolder {
        private SellInputHolder() {
            super(54, "&8Sell • Drag items into the tray");
        }
    }

    private static final class SellConfirmHolder extends ShopHolder {
        private final SellQuote quote;

        private SellConfirmHolder(SellQuote quote) {
            super(27, "&8Confirm Sale");
            this.quote = quote;
        }
    }
}
