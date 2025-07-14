package com.nenf.edenbazaar.gui;

import com.nenf.edenbazaar.EdenBazaar;
import com.nenf.edenbazaar.models.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern Bazaar GUI with MiniMessage support for beautiful, colorful interfaces.
 */
public class BazaarGUI implements InventoryHolder {
    
    private final EdenBazaar plugin;
    private final Inventory inventory;
    private final List<ShopItem> shopItems;
    
    public BazaarGUI(EdenBazaar plugin, List<ShopItem> shopItems) {
        this.plugin = plugin;
        this.shopItems = shopItems;
        
        // Parse MiniMessage title
        String titleString = plugin.getConfigManager().getGuiConfig()
            .getString("gui.title", "<bold><color:#FFB3C6>Mobile Bazaar</color></bold>");
        Component titleComponent = plugin.getConfigManager().parseMessage(titleString);
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(titleComponent);
        
        int size = plugin.getConfigManager().getGuiConfig().getInt("gui.size", 27);
        
        this.inventory = Bukkit.createInventory(this, size, legacyTitle);
        setupGUI();
    }
    
    private void setupGUI() {
        // Fill with background items
        ItemStack background = createBackgroundItem();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, background);
        }
        
        // Add shop items
        List<Integer> itemSlots = plugin.getConfigManager().getGuiConfig().getIntegerList("gui.item_slots");
        if (itemSlots.isEmpty()) {
            // Default slots
            itemSlots = List.of(10, 12, 14, 16, 22);
        }
        
        for (int i = 0; i < Math.min(shopItems.size(), itemSlots.size()); i++) {
            int slot = itemSlots.get(i);
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, shopItems.get(i).getItemStack());
            }
        }
        
        // Add info item
        int infoSlot = plugin.getConfigManager().getGuiConfig().getInt("gui.info_slot", 4);
        if (infoSlot < inventory.getSize()) {
            inventory.setItem(infoSlot, createInfoItem());
        }
        
        // Add close button
        int closeSlot = plugin.getConfigManager().getGuiConfig().getInt("gui.close_slot", 26);
        if (closeSlot < inventory.getSize()) {
            inventory.setItem(closeSlot, createCloseItem());
        }
    }
    
    private ItemStack createBackgroundItem() {
        String materialName = plugin.getConfigManager().getGuiConfig().getString("gui.background.material", "GRAY_STAINED_GLASS_PANE");
        Material material;
        
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createInfoItem() {
        Material material = Material.PAPER;
        String materialName = plugin.getConfigManager().getGuiConfig().getString("gui.info.material", "PAPER");
        
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            // Use default
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Parse MiniMessage name
            String nameString = plugin.getConfigManager().getGuiConfig()
                .getString("gui.info.name", "<bold><color:#FFB3C6>Bazaar Information</color></bold>");
            Component nameComponent = plugin.getConfigManager().parseMessage(nameString);
            String legacyName = LegacyComponentSerializer.legacySection().serialize(nameComponent);
            meta.setDisplayName(legacyName);
            
            List<String> lore = new ArrayList<>();
            List<String> loreTemplate = plugin.getConfigManager().getGuiConfig().getStringList("gui.info.lore");
            
            String locationName = plugin.getBazaarManager().getCurrentLocationName().orElse("Unknown");
            
            for (String line : loreTemplate) {
                // Replace placeholders and parse MiniMessage
                String processedLine = line
                    .replace("{location}", locationName)
                    .replace("{items}", String.valueOf(shopItems.size()));
                
                Component lineComponent = plugin.getConfigManager().parseMessage(processedLine);
                String legacyLine = LegacyComponentSerializer.legacySection().serialize(lineComponent);
                lore.add(legacyLine);
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    private ItemStack createCloseItem() {
        Material material = Material.BARRIER;
        String materialName = plugin.getConfigManager().getGuiConfig().getString("gui.close.material", "BARRIER");
        
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            // Use default
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Parse MiniMessage name
            String nameString = plugin.getConfigManager().getGuiConfig()
                .getString("gui.close.name", "<bold><color:#FF6B6B>Close</color></bold>");
            Component nameComponent = plugin.getConfigManager().parseMessage(nameString);
            String legacyName = LegacyComponentSerializer.legacySection().serialize(nameComponent);
            meta.setDisplayName(legacyName);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public List<ShopItem> getShopItems() {
        return shopItems;
    }
    
    public ShopItem getShopItem(int slot) {
        List<Integer> itemSlots = plugin.getConfigManager().getGuiConfig().getIntegerList("gui.item_slots");
        if (itemSlots.isEmpty()) {
            itemSlots = List.of(10, 12, 14, 16, 22);
        }
        
        int index = itemSlots.indexOf(slot);
        if (index >= 0 && index < shopItems.size()) {
            return shopItems.get(index);
        }
        
        return null;
    }
    
    public void openGUI(Player player) {
        player.openInventory(inventory);
    }
}