package me.thetealviper.commandshop.shop;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import me.thetealviper.commandshop.model.Price;

/**
 * Snapshots every recipe registered with Bukkit and evaluates price paths that
 * have fully known shop acquisition costs.
 */
public final class RecipeCatalog {
    private static final double EPSILON = 0.0000001D;

    private final Plugin plugin;
    private volatile List<RecipeSnapshot> recipes = List.of();
    private volatile Map<Material, Double> acquisitionCosts = Map.of();

    public RecipeCatalog(Plugin plugin) {
        this.plugin = plugin;
    }

    public int refresh(File destination) {
        List<RecipeSnapshot> snapshots = new ArrayList<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        int index = 0;
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            try {
                RecipeSnapshot snapshot = snapshot(recipe, index);
                snapshots.add(snapshot);
                documents.add(document(snapshot));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not document registered recipe #" + index, exception);
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("index", index);
                failed.put("type", recipe.getClass().getName());
                failed.put("error", exception.getClass().getSimpleName());
                documents.add(failed);
            }
            index++;
        }

        YamlConfiguration output = new YamlConfiguration();
        output.set("Generated_At", Instant.now().toString());
        output.set("Recipe_Count", index);
        output.set("Auditable_Recipe_Count",
                snapshots.stream().filter(RecipeSnapshot::auditable).count());
        output.set("Notes", List.of(
                "This file documents every recipe registered with Bukkit when CommandShop loaded or reloaded.",
                "Each Ingredients entry is one consumed slot; values within that entry are alternatives.",
                "Complex or server-specific recipes without exposed ingredients are documented but not price-audited.",
                "Recipes requiring metadata-bearing results or exact metadata ingredients are not price-audited.",
                "Cooking price audits cover ingredients only and do not assign a fuel cost."));
        output.set("Recipes", documents);
        try {
            output.save(destination);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not save recipe documentation to " + destination.getName(), exception);
        }
        recipes = Collections.unmodifiableList(snapshots);
        return index;
    }

    public List<ArbitrageFinding> audit(
            Map<Material, Price> buyPrices, Map<Material, Price> sellPrices) {
        Map<Material, Double> costs = calculateAcquisitionCosts(buyPrices);
        acquisitionCosts = Collections.unmodifiableMap(costs);
        List<ArbitrageFinding> findings = new ArrayList<>();

        for (Map.Entry<Material, Price> entry : sellPrices.entrySet()) {
            Price buy = buyPrices.get(entry.getKey());
            if (buy == null) {
                continue;
            }
            double buyUnit = unitPrice(buy);
            double sellUnit = unitPrice(entry.getValue());
            if (sellUnit > buyUnit + EPSILON) {
                findings.add(new ArbitrageFinding(
                        "direct:" + entry.getKey().name(),
                        "DIRECT",
                        entry.getKey(),
                        "buy-and-resell",
                        buyUnit,
                        sellUnit,
                        sellUnit / buyUnit));
            }
        }

        for (RecipeSnapshot recipe : recipes) {
            if (!recipe.auditable() || recipe.result() == null
                    || recipe.result().isAir() || recipe.resultAmount() <= 0) {
                continue;
            }
            Price sell = sellPrices.get(recipe.result());
            if (sell == null) {
                continue;
            }
            double ingredientCost = recipeCost(recipe, costs);
            if (!Double.isFinite(ingredientCost) || ingredientCost <= 0.0D) {
                continue;
            }
            double saleValue = unitPrice(sell) * recipe.resultAmount();
            if (saleValue > ingredientCost + EPSILON) {
                findings.add(new ArbitrageFinding(
                        "recipe:" + recipe.key() + ":" + recipe.result().name(),
                        "CRAFT",
                        recipe.result(),
                        recipe.key(),
                        ingredientCost,
                        saleValue,
                        saleValue / ingredientCost));
            }
        }
        findings.sort((left, right) -> left.id().compareToIgnoreCase(right.id()));
        return findings;
    }

    /**
     * Returns zero when no complete buy/crafting acquisition path is known.
     */
    public double saleRatio(Material material, Price sellPrice) {
        if (material == null || sellPrice == null) {
            return 0.0D;
        }
        Double cost = acquisitionCosts.get(material);
        if (cost == null || !Double.isFinite(cost) || cost <= 0.0D) {
            return 0.0D;
        }
        return unitPrice(sellPrice) / cost;
    }

    public Double acquisitionCost(Material material) {
        return acquisitionCosts.get(material);
    }

    private Map<Material, Double> calculateAcquisitionCosts(
            Map<Material, Price> buyPrices) {
        Map<Material, Double> costs = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Price> entry : buyPrices.entrySet()) {
            costs.put(entry.getKey(), unitPrice(entry.getValue()));
        }

        int maximumPasses = Math.max(1, Material.values().length);
        for (int pass = 0; pass < maximumPasses; pass++) {
            boolean changed = false;
            for (RecipeSnapshot recipe : recipes) {
                if (!recipe.auditable() || recipe.result() == null
                        || recipe.result().isAir() || recipe.resultAmount() <= 0) {
                    continue;
                }
                double total = recipeCost(recipe, costs);
                if (!Double.isFinite(total) || total <= 0.0D) {
                    continue;
                }
                double unit = total / recipe.resultAmount();
                Double existing = costs.get(recipe.result());
                if (existing == null || unit + EPSILON < existing) {
                    costs.put(recipe.result(), unit);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return costs;
    }

    private double recipeCost(RecipeSnapshot recipe, Map<Material, Double> costs) {
        double total = 0.0D;
        for (List<Material> alternatives : recipe.ingredients()) {
            double cheapest = Double.POSITIVE_INFINITY;
            for (Material alternative : alternatives) {
                Double cost = costs.get(alternative);
                if (cost != null && cost > 0.0D && cost < cheapest) {
                    cheapest = cost;
                }
            }
            if (!Double.isFinite(cheapest)) {
                return Double.NaN;
            }
            total += cheapest;
        }
        return total;
    }

    private RecipeSnapshot snapshot(Recipe recipe, int index) {
        String key = recipe instanceof Keyed
                ? ((Keyed) recipe).getKey().toString()
                : "unkeyed:" + index;
        ItemStack resultStack = recipe.getResult();
        Material result = resultStack == null ? null : resultStack.getType();
        int resultAmount = resultStack == null ? 0 : resultStack.getAmount();
        boolean plainResult = resultStack != null && !resultStack.hasItemMeta();
        List<List<Material>> ingredients = new ArrayList<>();
        List<String> shape = List.of();
        boolean exposed = true;

        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shaped = (ShapedRecipe) recipe;
            shape = List.of(shaped.getShape());
            Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
            for (String row : shaped.getShape()) {
                for (char symbol : row.toCharArray()) {
                    if (symbol == ' ') {
                        continue;
                    }
                    RecipeChoice choice = choices.get(symbol);
                    List<Material> alternatives = materials(choice);
                    if (alternatives.isEmpty()) {
                        exposed = false;
                    } else {
                        ingredients.add(alternatives);
                        exposed &= isPlainChoice(choice);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            for (RecipeChoice choice : ((ShapelessRecipe) recipe).getChoiceList()) {
                List<Material> alternatives = materials(choice);
                if (alternatives.isEmpty()) {
                    exposed = false;
                } else {
                    ingredients.add(alternatives);
                    exposed &= isPlainChoice(choice);
                }
            }
        } else if (recipe instanceof CookingRecipe) {
            exposed = addChoice(ingredients, ((CookingRecipe<?>) recipe).getInputChoice());
        } else if (recipe instanceof StonecuttingRecipe) {
            exposed = addChoice(ingredients, ((StonecuttingRecipe) recipe).getInputChoice());
        } else if (recipe instanceof SmithingRecipe) {
            SmithingRecipe smithing = (SmithingRecipe) recipe;
            if (recipe instanceof SmithingTransformRecipe) {
                exposed &= addChoice(ingredients,
                        ((SmithingTransformRecipe) recipe).getTemplate());
            } else if (recipe instanceof SmithingTrimRecipe) {
                exposed &= addChoice(ingredients,
                        ((SmithingTrimRecipe) recipe).getTemplate());
            }
            exposed &= addChoice(ingredients, smithing.getBase());
            exposed &= addChoice(ingredients, smithing.getAddition());
        } else {
            exposed = false;
        }

        boolean auditable = exposed && !ingredients.isEmpty()
                && plainResult && result != null && !result.isAir() && resultAmount > 0;
        return new RecipeSnapshot(
                index,
                key,
                recipe.getClass().getSimpleName(),
                result,
                resultAmount,
                immutableIngredients(ingredients),
                List.copyOf(shape),
                auditable);
    }

    private boolean addChoice(List<List<Material>> ingredients, RecipeChoice choice) {
        List<Material> alternatives = materials(choice);
        if (!alternatives.isEmpty()) {
            ingredients.add(alternatives);
            return isPlainChoice(choice);
        }
        return false;
    }

    private boolean isPlainChoice(RecipeChoice choice) {
        if (choice == null) {
            return false;
        }
        if (choice instanceof RecipeChoice.ExactChoice) {
            return ((RecipeChoice.ExactChoice) choice).getChoices().stream()
                    .anyMatch(item -> !item.hasItemMeta());
        }
        ItemStack example = choice.getItemStack();
        return example == null || !example.hasItemMeta();
    }

    private List<Material> materials(RecipeChoice choice) {
        if (choice == null) {
            return List.of();
        }
        List<Material> result = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice) {
            result.addAll(((RecipeChoice.MaterialChoice) choice).getChoices());
        } else if (choice instanceof RecipeChoice.ExactChoice) {
            for (ItemStack item : ((RecipeChoice.ExactChoice) choice).getChoices()) {
                result.add(item.getType());
            }
        } else {
            ItemStack example = choice.getItemStack();
            if (example != null) {
                result.add(example.getType());
            }
        }
        result.removeIf(material -> material == null || material.isAir());
        result.sort((left, right) -> left.name().compareTo(right.name()));
        return result.stream().distinct().toList();
    }

    private List<List<Material>> immutableIngredients(
            List<List<Material>> ingredients) {
        List<List<Material>> result = new ArrayList<>();
        for (List<Material> ingredient : ingredients) {
            result.add(List.copyOf(ingredient));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> document(RecipeSnapshot recipe) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", recipe.index());
        result.put("key", recipe.key());
        result.put("type", recipe.type());
        result.put("result", recipe.result() == null
                ? "UNKNOWN" : recipe.result().name());
        result.put("result_amount", recipe.resultAmount());
        result.put("auditable", recipe.auditable());
        if (!recipe.shape().isEmpty()) {
            result.put("shape", recipe.shape());
        }
        List<List<String>> ingredients = new ArrayList<>();
        for (List<Material> alternatives : recipe.ingredients()) {
            ingredients.add(alternatives.stream()
                    .map(Material::name)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .toList());
        }
        result.put("ingredients", ingredients);
        return result;
    }

    private double unitPrice(Price price) {
        return price.price() / price.amount();
    }

    public record ArbitrageFinding(
            String id,
            String type,
            Material material,
            String source,
            double acquisitionCost,
            double saleValue,
            double ratio) {
    }

    private record RecipeSnapshot(
            int index,
            String key,
            String type,
            Material result,
            int resultAmount,
            List<List<Material>> ingredients,
            List<String> shape,
            boolean auditable) {
    }
}
