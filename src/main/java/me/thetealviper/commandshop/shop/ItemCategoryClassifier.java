package me.thetealviper.commandshop.shop;

import java.util.Set;

import org.bukkit.Material;

/**
 * Classifies every Bukkit material using stable API properties and material-name
 * conventions. Name-based checks deliberately allow newer Minecraft materials to
 * be categorized without requiring a plugin update for every server release.
 */
public final class ItemCategoryClassifier {
    public enum Category {
        FOOD,
        MATERIALS,
        ORES,
        OTHER
    }

    private static final Set<String> FARM_GOODS = Set.of(
            "WHEAT", "CARROT", "POTATO", "POISONOUS_POTATO", "BEETROOT",
            "COCOA_BEANS", "SUGAR_CANE", "SUGAR", "CACTUS", "NETHER_WART",
            "BAMBOO", "KELP", "DRIED_KELP", "SWEET_BERRIES", "GLOW_BERRIES",
            "CHORUS_FRUIT", "PUMPKIN", "MELON", "BROWN_MUSHROOM",
            "RED_MUSHROOM", "EGG", "MILK_BUCKET");

    private static final Set<String> MINERAL_RESOURCES = Set.of(
            "COAL", "CHARCOAL",
            "RAW_IRON", "RAW_COPPER", "RAW_GOLD",
            "IRON_NUGGET", "COPPER_NUGGET", "GOLD_NUGGET",
            "IRON_INGOT", "COPPER_INGOT", "GOLD_INGOT",
            "DIAMOND", "EMERALD", "LAPIS_LAZULI", "REDSTONE", "QUARTZ",
            "AMETHYST_SHARD", "ANCIENT_DEBRIS", "GILDED_BLACKSTONE",
            "NETHERITE_SCRAP", "NETHERITE_INGOT");

    private static final Set<String> MINERAL_STORAGE_BLOCKS = Set.of(
            "COAL_BLOCK", "RAW_IRON_BLOCK", "RAW_COPPER_BLOCK", "RAW_GOLD_BLOCK",
            "IRON_BLOCK", "COPPER_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK",
            "EMERALD_BLOCK", "LAPIS_BLOCK", "REDSTONE_BLOCK", "NETHERITE_BLOCK",
            "AMETHYST_BLOCK", "BUDDING_AMETHYST");

    private static final Set<String> MINED_MINERAL_PRODUCTS = Set.of(
            "CLAY", "CLAY_BALL", "FLINT", "GLOWSTONE", "GLOWSTONE_DUST");

    private static final Set<String> UTILITY_BLOCKS = Set.of(
            "CRAFTING_TABLE", "CRAFTER", "FURNACE", "BLAST_FURNACE", "SMOKER",
            "STONECUTTER", "SMITHING_TABLE", "CARTOGRAPHY_TABLE",
            "FLETCHING_TABLE", "LOOM", "GRINDSTONE", "ENCHANTING_TABLE",
            "BREWING_STAND", "CAULDRON", "ANVIL", "CHIPPED_ANVIL",
            "DAMAGED_ANVIL", "CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL",
            "HOPPER", "DISPENSER", "DROPPER", "CHISELED_BOOKSHELF", "BOOKSHELF",
            "LECTERN", "JUKEBOX", "NOTE_BLOCK", "PISTON", "STICKY_PISTON",
            "OBSERVER", "TARGET", "DAYLIGHT_DETECTOR", "REDSTONE_LAMP", "LEVER",
            "TRIPWIRE_HOOK", "REPEATER", "COMPARATOR", "COMPOSTER", "BEEHIVE",
            "BEE_NEST", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "BEACON",
            "CONDUIT", "LODESTONE", "RESPAWN_ANCHOR", "END_PORTAL_FRAME", "TNT",
            "BELL", "FLOWER_POT", "DECORATED_POT", "SCAFFOLDING", "LADDER",
            "CHAIN", "END_ROD", "LIGHTNING_ROD", "SPONGE", "WET_SPONGE",
            "SEA_PICKLE", "JACK_O_LANTERN", "CARVED_PUMPKIN", "HEAVY_CORE",
            "DRAGON_EGG", "SLIME_BLOCK", "HONEY_BLOCK", "CANDLE", "TORCH",
            "LANTERN", "CAMPFIRE", "RAIL", "SCULK_SENSOR",
            "CALIBRATED_SCULK_SENSOR", "SCULK_SHRIEKER", "SCULK_CATALYST",
            "FROGSPAWN", "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL");

    private static final Set<String> NON_BUILDING_PLANTS = Set.of(
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET",
            "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP", "PINK_TULIP",
            "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE",
            "TORCHFLOWER", "PITCHER_PLANT", "SUNFLOWER", "LILAC", "ROSE_BUSH",
            "PEONY", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN",
            "DEAD_BUSH", "VINE", "GLOW_LICHEN", "HANGING_ROOTS",
            "SPORE_BLOSSOM", "BIG_DRIPLEAF", "SMALL_DRIPLEAF", "LILY_PAD");

    private ItemCategoryClassifier() {
    }

    public static Category classify(Material material) {
        String name = material.name();
        if (isFoodOrCrop(material, name)) {
            return Category.FOOD;
        }
        if (isOreOrMineral(name)) {
            return Category.ORES;
        }
        if (isBuildingMaterial(material, name)) {
            return Category.MATERIALS;
        }
        return Category.OTHER;
    }

    private static boolean isFoodOrCrop(Material material, String name) {
        return material.isEdible()
                || FARM_GOODS.contains(name)
                || name.endsWith("_SEEDS")
                || name.endsWith("_SAPLING")
                || name.endsWith("_PROPAGULE");
    }

    private static boolean isOreOrMineral(String name) {
        return name.endsWith("_ORE")
                || name.contains("AMETHYST")
                || MINED_MINERAL_PRODUCTS.contains(name)
                || MINERAL_RESOURCES.contains(name)
                || MINERAL_STORAGE_BLOCKS.contains(name)
                || isUncutCopperStorageBlock(name);
    }

    private static boolean isUncutCopperStorageBlock(String name) {
        String unwaxed = name.startsWith("WAXED_") ? name.substring(6) : name;
        return unwaxed.equals("COPPER_BLOCK")
                || unwaxed.equals("EXPOSED_COPPER")
                || unwaxed.equals("WEATHERED_COPPER")
                || unwaxed.equals("OXIDIZED_COPPER");
    }

    private static boolean isBuildingMaterial(Material material, String name) {
        return material.isBlock()
                && material.isItem()
                && !material.isAir()
                && !isUtilityOrFunctionalBlock(name)
                && (isConstructionVariant(name) || !material.isInteractable())
                && !NON_BUILDING_PLANTS.contains(name);
    }

    private static boolean isConstructionVariant(String name) {
        return name.endsWith("_STAIRS")
                || name.endsWith("_SLAB")
                || name.endsWith("_WALL")
                || name.endsWith("_FENCE")
                || name.endsWith("_PLANKS")
                || name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.endsWith("_WOOL")
                || name.endsWith("_CARPET")
                || name.equals("GLASS")
                || name.endsWith("_GLASS")
                || name.endsWith("_GLASS_PANE");
    }

    private static boolean isUtilityOrFunctionalBlock(String name) {
        return UTILITY_BLOCKS.contains(name)
                || name.endsWith("_SHULKER_BOX")
                || name.endsWith("_BED")
                || name.endsWith("_CANDLE")
                || name.endsWith("_BANNER")
                || name.endsWith("_HANGING_SIGN")
                || name.endsWith("_SIGN")
                || name.endsWith("_HEAD")
                || name.endsWith("_SKULL")
                || name.endsWith("_TORCH")
                || name.endsWith("_LANTERN")
                || name.endsWith("_CAMPFIRE")
                || name.endsWith("_RAIL")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_DOOR")
                || name.endsWith("_FENCE_GATE")
                || name.endsWith("_EGG")
                || name.endsWith("COPPER_BULB");
    }
}
