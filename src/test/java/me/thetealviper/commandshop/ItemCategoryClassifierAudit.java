package me.thetealviper.commandshop;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.Material;

/**
 * Dependency-free regression audit. Run this main class after test compilation.
 */
public final class ItemCategoryClassifierAudit {
    private ItemCategoryClassifierAudit() {
    }

    public static void main(String[] args) {
        expect(Material.APPLE, ItemCategoryClassifier.Category.FOOD);
        expect(Material.WHEAT, ItemCategoryClassifier.Category.FOOD);

        expect(Material.DIAMOND_ORE, ItemCategoryClassifier.Category.ORES);
        expect(Material.IRON_INGOT, ItemCategoryClassifier.Category.ORES);
        expect(Material.ANCIENT_DEBRIS, ItemCategoryClassifier.Category.ORES);
        expect(Material.GILDED_BLACKSTONE, ItemCategoryClassifier.Category.ORES);
        expect(Material.COPPER_BLOCK, ItemCategoryClassifier.Category.ORES);
        expect(Material.EXPOSED_COPPER, ItemCategoryClassifier.Category.ORES);
        expect(Material.WAXED_OXIDIZED_COPPER, ItemCategoryClassifier.Category.ORES);

        expect(Material.CUT_COPPER_STAIRS, ItemCategoryClassifier.Category.MATERIALS);
        expect(Material.MUD_BRICKS, ItemCategoryClassifier.Category.MATERIALS);
        expect(Material.MUD_BRICK_STAIRS, ItemCategoryClassifier.Category.MATERIALS);
        expect(Material.OAK_LOG, ItemCategoryClassifier.Category.MATERIALS);
        expect(Material.CRAFTING_TABLE, ItemCategoryClassifier.Category.MATERIALS);

        expect(Material.ARMOR_STAND, ItemCategoryClassifier.Category.OTHER);
        expect(Material.FIREWORK_ROCKET, ItemCategoryClassifier.Category.OTHER);
        expect(Material.DIAMOND_SWORD, ItemCategoryClassifier.Category.OTHER);

        Map<ItemCategoryClassifier.Category, Integer> totals =
                new EnumMap<>(ItemCategoryClassifier.Category.class);
        for (Material material : Material.values()) {
            totals.merge(ItemCategoryClassifier.classify(material), 1, Integer::sum);
        }
        for (ItemCategoryClassifier.Category category : ItemCategoryClassifier.Category.values()) {
            if (totals.getOrDefault(category, 0) == 0) {
                throw new AssertionError("No Bukkit materials classified as " + category);
            }
        }
        System.out.println("Classified all " + Material.values().length + " Bukkit materials: " + totals);
    }

    private static void expect(Material material, ItemCategoryClassifier.Category expected) {
        ItemCategoryClassifier.Category actual = ItemCategoryClassifier.classify(material);
        if (actual != expected) {
            throw new AssertionError(material + " expected " + expected + " but was " + actual);
        }
    }
}
