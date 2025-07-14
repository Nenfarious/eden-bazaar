package com.nenf.edenbazaar.managers;

import com.nenf.edenbazaar.EdenBazaar;
import com.nenf.edenbazaar.models.LootItem;
import com.nenf.edenbazaar.models.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Modern loot generator with MiniMessage support for beautiful item formatting.
 */
public class LootGenerator {
    
    private final EdenBazaar plugin;
    
    public LootGenerator(EdenBazaar plugin) {
        this.plugin = plugin;
    }
    
    public List<ShopItem> generateShopInventory() {
        List<ShopItem> inventory = new ArrayList<>();
        Map<String, List<LootItem>> lootPools = plugin.getConfigManager().getLootPools();
        
        // Read max items from configuration snapshot for consistency
        int maxItems = plugin.getConfigManager().getConfigSnapshot().maxShopItems();
        
        // Ensure we have loot pools configured
        if (lootPools.isEmpty()) {
            plugin.getLogger().warning("No loot pools configured! Cannot generate shop inventory.");
            return inventory;
        }
        
        for (int i = 0; i < maxItems; i++) {
            ShopItem item = generateRandomItem(lootPools);
            if (item != null) {
                inventory.add(item);
            }
        }
        
        plugin.getLogger().fine("Generated " + inventory.size() + " items for shop inventory");
        return inventory;
    }
    
    private ShopItem generateRandomItem(Map<String, List<LootItem>> lootPools) {
        // Create weighted list of all items
        List<WeightedLootItem> weightedItems = new ArrayList<>();
        
        for (Map.Entry<String, List<LootItem>> entry : lootPools.entrySet()) {
            String tier = entry.getKey();
            List<LootItem> items = entry.getValue();
            
            for (LootItem item : items) {
                weightedItems.add(new WeightedLootItem(item, tier));
            }
        }
        
        if (weightedItems.isEmpty()) {
            return null;
        }
        
        // Calculate total weight - using modern stream operations
        int totalWeight = weightedItems.stream()
            .mapToInt(item -> item.lootItem.weight())
            .sum();
        
        // Prevent division by zero
        if (totalWeight <= 0) {
            plugin.getLogger().warning("All loot items have zero or negative weight! Using first available item.");
            WeightedLootItem firstItem = weightedItems.getFirst();
            return createShopItem(firstItem.lootItem, firstItem.tier);
        }
        
        // Select random item based on weight - using ThreadLocalRandom for better performance
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;
        
        for (WeightedLootItem weightedItem : weightedItems) {
            currentWeight += weightedItem.lootItem.weight();
            if (randomWeight < currentWeight) {
                return createShopItem(weightedItem.lootItem, weightedItem.tier);
            }
        }
        
        // Fallback to first item - using new sequenced collection methods
        WeightedLootItem firstItem = weightedItems.getFirst();
        return createShopItem(firstItem.lootItem, firstItem.tier);
    }
    
    private ShopItem createShopItem(LootItem lootItem, String tier) {
        ItemStack itemStack = new ItemStack(lootItem.material());
        ItemMeta meta = itemStack.getItemMeta();
        
        // Generate random price - improved with validation
        int priceRange = lootItem.maxPrice() - lootItem.minPrice();
        int price = lootItem.hasFixedPrice() 
            ? lootItem.minPrice()
            : ThreadLocalRandom.current().nextInt(priceRange + 1) + lootItem.minPrice();
        
        // Set item name and lore using MiniMessage
        if (meta != null) {
            // Parse item name format from config with MiniMessage
            String itemNameFormat = plugin.getConfigManager().getGuiConfig()
                .getString("items.name_format", "<white>{item}</white> <color:#ADB5BD>({tier})</color>");
            
            String processedName = itemNameFormat
                .replace("{item}", formatItemName(lootItem.material().name()))
                .replace("{tier}", tier.toUpperCase());
            
            Component nameComponent = plugin.getConfigManager().parseMessage(processedName);
            String legacyName = LegacyComponentSerializer.legacySection().serialize(nameComponent);
            meta.setDisplayName(legacyName);
            
            // Parse lore template with MiniMessage
            List<String> lore = new ArrayList<>();
            List<String> loreTemplate = plugin.getConfigManager().getGuiConfig().getStringList("items.lore_template");
            
            for (String line : loreTemplate) {
                String processedLine = line
                    .replace("{price}", String.valueOf(price))
                    .replace("{tier}", tier.toUpperCase())
                    .replace("{item}", formatItemName(lootItem.material().name()));
                
                Component lineComponent = plugin.getConfigManager().parseMessage(processedLine);
                String legacyLine = LegacyComponentSerializer.legacySection().serialize(lineComponent);
                lore.add(legacyLine);
            }
            
            meta.setLore(lore);
            itemStack.setItemMeta(meta);
        }
        
        return new ShopItem(itemStack, price, tier);
    }
    
    private String formatItemName(String materialName) {
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();
        
        for (String part : parts) {
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        
        return formatted.toString();
    }
    
    private record WeightedLootItem(LootItem lootItem, String tier) {
        WeightedLootItem {
            if (lootItem == null) {
                throw new IllegalArgumentException("LootItem cannot be null");
            }
            if (tier == null || tier.trim().isEmpty()) {
                throw new IllegalArgumentException("Tier cannot be null or empty");
            }
        }
    }
}