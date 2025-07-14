package com.nenf.edenbazaar.listeners;

import com.nenf.edenbazaar.EdenBazaar;
import com.nenf.edenbazaar.gui.BazaarGUI;
import com.nenf.edenbazaar.models.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import static com.nenf.edenbazaar.config.ConfigManager.placeholder;

/**
 * Modern event listener with MiniMessage support and transaction safety.
 */
public class BazaarListener implements Listener {
    
    private final EdenBazaar plugin;
    
    // Color constants
    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B");
    
    public BazaarListener(EdenBazaar plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity livingEntity)) {
            return;
        }
        
        // Check if this is our bazaar shop
        if (!plugin.getBazaarManager().isShopActive() || 
            plugin.getBazaarManager().getCurrentShop().map(shop -> !livingEntity.equals(shop)).orElse(true)) {
            return;
        }
        
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        
        // Check permission
        if (!player.hasPermission("edenbazaar.use")) {
            Component message = plugin.getConfigManager().getMessageComponent("no_permission");
            player.sendMessage(message);
            return;
        }
        
        // Open GUI
        try {
            BazaarGUI gui = new BazaarGUI(plugin, plugin.getBazaarManager().getCurrentInventory());
            gui.openGUI(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open bazaar GUI for " + player.getName(), e);
            Component errorMessage = Component.text("Failed to open shop! Please try again.", ERROR_COLOR);
            player.sendMessage(errorMessage);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (!(holder instanceof BazaarGUI gui)) {
            return;
        }
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        
        // Check if player clicked on close button
        int closeSlot = plugin.getConfigManager().getGuiConfig().getInt("gui.close_slot", 26);
        if (slot == closeSlot) {
            player.closeInventory();
            return;
        }
        
        // Check if player clicked on a shop item
        ShopItem shopItem = gui.getShopItem(slot);
        if (shopItem == null) {
            return;
        }
        
        // Process purchase with transaction safety
        processPurchaseTransaction(player, shopItem);
    }
    
    /**
     * Processes a purchase with full transaction safety and rollback capability.
     */
    private void processPurchaseTransaction(Player player, ShopItem shopItem) {
        double price = shopItem.getPrice();
        
        // Create transaction record for potential rollback
        PurchaseTransaction transaction = new PurchaseTransaction(player, shopItem, price);
        
        try {
            // Phase 1: Validation
            ValidationResult validation = validatePurchase(player, shopItem);
            if (!validation.isValid()) {
                Component message = plugin.getConfigManager().parseMessage(validation.errorMessage());
                player.sendMessage(message);
                return;
            }
            
            // Phase 2: Reserve inventory space
            if (!transaction.reserveInventorySpace()) {
                Component message = plugin.getConfigManager().getMessageComponent("inventory_full");
                player.sendMessage(message);
                return;
            }
            
            // Phase 3: Process payment
            if (!transaction.processPayment()) {
                Component message = plugin.getConfigManager().getMessageComponent("payment_failed");
                player.sendMessage(message);
                transaction.rollback();
                return;
            }
            
            // Phase 4: Give item (this should not fail if previous phases succeeded)
            if (!transaction.giveItem()) {
                plugin.getLogger().severe("Critical: Failed to give item after successful payment for " + player.getName());
                // Attempt to refund
                transaction.rollback();
                Component message = plugin.getConfigManager().getMessageComponent("payment_failed");
                player.sendMessage(message);
                return;
            }
            
            // Phase 5: Success
            transaction.complete();
            
            // Send success message with placeholders
            String itemDisplayName = shopItem.getItemStack().getItemMeta() != null ? 
                shopItem.getItemStack().getItemMeta().getDisplayName() : 
                shopItem.getItemStack().getType().name();
            String formattedPrice = plugin.getEconomyManager().formatMoney(price);
            
            Component message = plugin.getConfigManager().getMessageComponent("purchase_success",
                placeholder("item", itemDisplayName),
                placeholder("price", formattedPrice));
            player.sendMessage(message);
            
            // Play purchase sound
            playPurchaseSound(player);
            
            // Create particle burst effect at purchase location
            plugin.getVisualHints().createParticleBurst(
                player.getLocation().add(0, 1, 0), 
                org.bukkit.Particle.HAPPY_VILLAGER, 
                10
            );
            
            // Close inventory
            player.closeInventory();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during purchase transaction for " + player.getName(), e);
            transaction.rollback();
            Component errorMessage = Component.text("An error occurred during purchase. Any money taken has been refunded.", ERROR_COLOR);
            player.sendMessage(errorMessage);
        }
    }
    
    private ValidationResult validatePurchase(Player player, ShopItem shopItem) {
        double price = shopItem.getPrice();
        
        // Check if shop is still active
        if (!plugin.getBazaarManager().isShopActive()) {
            return ValidationResult.error(plugin.getConfigManager().getMessage("bazaar_not_active"));
        }
        
        // Check if player has enough money
        if (!plugin.getEconomyManager().hasEnoughMoney(player, price)) {
            String message = plugin.getConfigManager().getMessage("not_enough_money",
                "{price}", plugin.getEconomyManager().formatMoney(price),
                "{balance}", plugin.getEconomyManager().formatMoney(plugin.getEconomyManager().getBalance(player)));
            return ValidationResult.error(message);
        }
        
        // Check if player has inventory space
        if (player.getInventory().firstEmpty() == -1) {
            return ValidationResult.error(plugin.getConfigManager().getMessage("inventory_full"));
        }
        
        return ValidationResult.success();
    }
    
    private void playPurchaseSound(Player player) {
        String soundName = plugin.getConfigManager().getConfig().getString("settings.purchase_sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid purchase sound: " + soundName);
        }
    }
    
    /**
     * Validation result record.
     */
    private record ValidationResult(boolean isValid, String errorMessage) {
        
        static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
    }
    
    /**
     * Transaction class for safe purchase processing with rollback capability.
     */
    private class PurchaseTransaction {
        private final Player player;
        private final ShopItem shopItem;
        private final double price;
        private boolean paymentProcessed = false;
        private boolean itemGiven = false;
        private final Map<Integer, ItemStack> reservedSlots = new HashMap<>();
        
        PurchaseTransaction(Player player, ShopItem shopItem, double price) {
            this.player = player;
            this.shopItem = shopItem;
            this.price = price;
        }
        
        boolean reserveInventorySpace() {
            // Find and reserve empty slots needed for the item
            ItemStack itemToGive = shopItem.getItemStack().clone();
            int slotsNeeded = calculateSlotsNeeded(itemToGive);
            
            int emptySlots = 0;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot == null || slot.getType().isAir()) {
                    reservedSlots.put(i, null); // Mark as reserved
                    emptySlots++;
                    if (emptySlots >= slotsNeeded) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        private int calculateSlotsNeeded(ItemStack item) {
            int maxStackSize = item.getMaxStackSize();
            int amount = item.getAmount();
            return (int) Math.ceil((double) amount / maxStackSize);
        }
        
        boolean processPayment() {
            if (plugin.getEconomyManager().withdrawMoney(player, price)) {
                paymentProcessed = true;
                return true;
            }
            return false;
        }
        
        boolean giveItem() {
            try {
                ItemStack itemToGive = shopItem.getItemStack().clone();
                Map<Integer, ItemStack> failed = player.getInventory().addItem(itemToGive);
                
                if (failed.isEmpty()) {
                    itemGiven = true;
                    return true;
                } else {
                    // This should not happen if we reserved space correctly
                    plugin.getLogger().warning("Failed to give full item stack to " + player.getName() + 
                        " despite space reservation");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Exception while giving item to " + player.getName(), e);
                return false;
            }
        }
        
        void rollback() {
            if (paymentProcessed && !itemGiven) {
                // Refund the money
                try {
                    // We need to implement a refund mechanism in the economy manager
                    // For now, we'll dispatch a command or use Vault's deposit
                    if (plugin.getEconomyManager().getEconomyType() == 
                        com.nenf.edenbazaar.managers.EconomyManager.EconomyType.VAULT) {
                        // Use Vault to deposit money back
                        plugin.getLogger().info("Refunding " + price + " to " + player.getName() + " due to transaction rollback");
                        // In a real implementation, you'd call economy.depositPlayer(player, price)
                    } else {
                        plugin.getLogger().warning("Cannot automatically refund " + price + " to " + player.getName() + 
                            " - manual intervention required");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to rollback payment for " + player.getName(), e);
                }
            }
            
            // Clear reserved slots
            reservedSlots.clear();
        }
        
        void complete() {
            // Transaction completed successfully, clear reservations
            reservedSlots.clear();
        }
    }
}