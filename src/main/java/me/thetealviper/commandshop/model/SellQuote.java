package me.thetealviper.commandshop.model;

import java.util.Map;

import org.bukkit.Material;

public record SellQuote(Map<Material, Integer> amounts, double total, int totalItems) {
}
