package com.nenf.edenbazaar.models;

import org.bukkit.Material;

import java.util.Objects;

/**
 * Immutable record representing a loot item with material, price range, and weight.
 * 
 * @param material the material type - cannot be null
 * @param minPrice minimum price - must be positive
 * @param maxPrice maximum price - must be >= minPrice
 * @param weight spawn weight - must be positive
 */
public record LootItem(
    Material material,
    int minPrice,
    int maxPrice,
    int weight
) {
    
    /**
     * Compact constructor with validation.
     */
    public LootItem {
        Objects.requireNonNull(material, "Material cannot be null");
        
        if (minPrice <= 0) {
            throw new IllegalArgumentException("Minimum price must be positive, got: " + minPrice);
        }
        
        if (maxPrice <= 0) {
            throw new IllegalArgumentException("Maximum price must be positive, got: " + maxPrice);
        }
        
        if (minPrice > maxPrice) {
            throw new IllegalArgumentException(
                "Minimum price (%d) cannot be greater than maximum price (%d)"
                    .formatted(minPrice, maxPrice)
            );
        }
        
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive, got: " + weight);
        }
    }
    
    /**
     * Creates a new LootItem with the specified weight.
     * 
     * @param newWeight the new weight (must be positive)
     * @return a new LootItem instance with updated weight
     */
    public LootItem withWeight(int newWeight) {
        return new LootItem(material, minPrice, maxPrice, newWeight);
    }
    
    /**
     * Creates a new LootItem with the specified price range.
     * 
     * @param newMinPrice new minimum price (must be positive)
     * @param newMaxPrice new maximum price (must be >= newMinPrice)
     * @return a new LootItem instance with updated price range
     */
    public LootItem withPriceRange(int newMinPrice, int newMaxPrice) {
        return new LootItem(material, newMinPrice, newMaxPrice, weight);
    }
    
    /**
     * Gets the price range span.
     * 
     * @return the difference between max and min price
     */
    public int priceRange() {
        return maxPrice - minPrice;
    }
    
    /**
     * Checks if this item has a fixed price (min equals max).
     * 
     * @return true if min and max prices are equal
     */
    public boolean hasFixedPrice() {
        return minPrice == maxPrice;
    }
}