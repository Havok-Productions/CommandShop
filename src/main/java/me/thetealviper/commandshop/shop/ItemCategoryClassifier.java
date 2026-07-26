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

    private static final Set<String> CRAFTING_MATERIALS = Set.of(
            "STICK", "STRING", "FLINT", "FEATHER", "LEATHER", "RABBIT_HIDE",
            "PAPER", "BOOK", "CLAY_BALL", "BRICK", "NETHER_BRICK",
            "BONE", "BONE_MEAL", "GUNPOWDER", "BLAZE_ROD", "BLAZE_POWDER",
            "MAGMA_CREAM", "SLIME_BALL", "HONEYCOMB", "INK_SAC",
            "GLOW_INK_SAC", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS",
            "NAUTILUS_SHELL", "SCUTE", "ARMADILLO_SCUTE", "PHANTOM_MEMBRANE",
            "GHAST_TEAR", "SPIDER_EYE", "FERMENTED_SPIDER_EYE",
            "GLISTERING_MELON_SLICE", "RABBIT_FOOT", "TURTLE_SCUTE",
            "ECHO_SHARD", "DISC_FRAGMENT_5", "NETHER_STAR", "RESIN_BRICK");

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
        if (isBuildingOrCraftingMaterial(material, name)) {
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

    private static boolean isBuildingOrCraftingMaterial(Material material, String name) {
        if (material.isBlock() && material.isItem()
                && !name.equals("AIR")
                && !name.equals("CAVE_AIR")
                && !name.equals("VOID_AIR")) {
            return true;
        }
        return CRAFTING_MATERIALS.contains(name)
                || name.endsWith("_DYE")
                || name.endsWith("_POTTERY_SHERD")
                || name.endsWith("_BANNER_PATTERN")
                || name.endsWith("_SMITHING_TEMPLATE");
    }
}
