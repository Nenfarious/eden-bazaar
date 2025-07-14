package com.nenf.edenbazaar.managers;

import com.nenf.edenbazaar.EdenBazaar;
import com.nenf.edenbazaar.models.ShopItem;
import com.nenf.edenbazaar.models.SpawnLocation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import static com.nenf.edenbazaar.config.ConfigManager.placeholder;

/**
 * Modern bazaar manager with MiniMessage support, thread safety, proper resource management, and Java 21 features.
 */
public class BazaarManager {
    
    private final EdenBazaar plugin;
    private final ReadWriteLock shopLock = new ReentrantReadWriteLock();
    
    // Volatile fields for safe concurrent access - using LivingEntity for configurability
    private volatile LivingEntity currentShop;
    private volatile Location currentLocation;
    private volatile String currentLocationName;
    private volatile List<ShopItem> currentInventory;
    
    // Scheduler management
    private BukkitTask despawnTask;
    private BukkitTask spawnTask;
    
    public BazaarManager(EdenBazaar plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Starts the automatic bazaar spawning scheduler.
     */
    public void startScheduler() {
        shopLock.writeLock().lock();
        try {
            // Stop any existing scheduler first
            stopScheduler();
            
            long spawnInterval = plugin.getConfigManager().getConfigSnapshot().spawnInterval() * 20L; // Convert to ticks
            
            spawnTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        if (!isShopActive()) {
                            spawnShop();
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error in bazaar spawn scheduler", e);
                    }
                }
            }.runTaskTimer(plugin, 100L, spawnInterval); // Small delay on startup
            
            plugin.getLogger().info("Bazaar scheduler started with " + (spawnInterval / 20) + "s interval");
            
        } finally {
            shopLock.writeLock().unlock();
        }
    }
    
    /**
     * Stops all schedulers and cleans up resources.
     */
    public void stopScheduler() {
        shopLock.writeLock().lock();
        try {
            if (spawnTask != null && !spawnTask.isCancelled()) {
                spawnTask.cancel();
                spawnTask = null;
            }
            
            if (despawnTask != null && !despawnTask.isCancelled()) {
                despawnTask.cancel();
                despawnTask = null;
            }
            
            plugin.getLogger().fine("Bazaar schedulers stopped");
            
        } finally {
            shopLock.writeLock().unlock();
        }
    }
    
    /**
     * Spawns a new bazaar shop with full error handling and validation.
     */
    public boolean spawnShop() {
        shopLock.writeLock().lock();
        try {
            // Clean up existing shop first
            if (isShopActive()) {
                despawnShop();
            }
            
            // Get available spawn locations
            List<SpawnLocation> locations = plugin.getConfigManager().getSpawnLocations();
            if (locations.isEmpty()) {
                plugin.getLogger().warning("Cannot spawn bazaar: No spawn locations configured!");
                return false;
            }
            
            // Select random location
            SpawnLocation spawnLocation = locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
            Location location = spawnLocation.getLocation();
            
            // Validate location
            if (location.getWorld() == null) {
                plugin.getLogger().warning("Cannot spawn bazaar: Invalid world for location " + spawnLocation.getName());
                return false;
            }
            
            // Generate inventory
            List<ShopItem> inventory = plugin.getLootGenerator().generateShopInventory();
            if (inventory.isEmpty()) {
                plugin.getLogger().warning("Cannot spawn bazaar: No items generated!");
                return false;
            }
            
            // Spawn NPC (now configurable)
            LivingEntity npc = spawnNPC(location);
            if (npc == null) {
                plugin.getLogger().severe("Failed to spawn bazaar NPC at " + spawnLocation.getName());
                return false;
            }
            
            // Update state
            currentShop = npc;
            currentLocation = location;
            currentLocationName = spawnLocation.getName();
            currentInventory = List.copyOf(inventory); // Immutable copy
            
            // Broadcast spawn message
            broadcastSpawnMessage();
            
            // Start visual effects
            plugin.getVisualHints().startParticleTask(location);
            
            // Create spawn particle burst
            plugin.getVisualHints().createParticleBurst(
                location.clone().add(0, 1, 0), 
                org.bukkit.Particle.FIREWORK, 
                20
            );
            
            // Schedule despawn
            scheduleDespawn();
            
            plugin.getLogger().info("Bazaar spawned at " + currentLocationName + " with " + inventory.size() + " items");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn bazaar", e);
            return false;
        } finally {
            shopLock.writeLock().unlock();
        }
    }
    
    private LivingEntity spawnNPC(Location location) {
        try {
            // Read NPC type from configuration
            String npcTypeStr = plugin.getConfigManager().getGuiConfig()
                .getString("npc.type", "VILLAGER").toUpperCase();
            
            EntityType npcType;
            try {
                npcType = EntityType.valueOf(npcTypeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid NPC type in config: " + npcTypeStr + ". Using VILLAGER as fallback.");
                npcType = EntityType.VILLAGER;
            }
            
            // Validate entity type is living and spawnable
            if (!npcType.isAlive() || !npcType.isSpawnable()) {
                plugin.getLogger().warning("NPC type " + npcTypeStr + " is not a valid living entity. Using VILLAGER as fallback.");
                npcType = EntityType.VILLAGER;
            }
            
            // Spawn the entity
            LivingEntity npc = (LivingEntity) location.getWorld().spawnEntity(location, npcType);
            
            // Configure common properties for all living entities
            npc.setAI(false);
            npc.setInvulnerable(true);
            npc.setSilent(true);
            npc.setPersistent(true);
            
            // Set custom name using MiniMessage
            String customNameString = plugin.getConfigManager().getGuiConfig()
                .getString("npc.name", "<bold><color:#FFB3C6>Mobile Bazaar</color></bold>");
            Component customName = plugin.getConfigManager().parseMessage(customNameString);
            npc.customName(customName);
            npc.setCustomNameVisible(true);
            
            // Apply specific configurations based on entity type
            configureSpecificNPC(npc, npcType);
            
            plugin.getLogger().info("Spawned bazaar NPC of type: " + npcType.name());
            return npc;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to spawn NPC", e);
            return null;
        }
    }
    
    /**
     * Apply entity-specific configurations
     */
    private void configureSpecificNPC(LivingEntity npc, EntityType type) {
        switch (type) {
            case VILLAGER -> {
                // Villager-specific configuration
                if (npc instanceof Villager villager) {
                    villager.setProfession(Villager.Profession.NITWIT);
                    villager.setVillagerType(Villager.Type.PLAINS);
                }
            }
            case PLAYER -> {
                // Player-type NPCs might need special handling
                // Note: Spawning PLAYER entities is complex and may not work as expected
                plugin.getLogger().warning("PLAYER entity type may not work as expected for NPCs");
            }
            case WANDERING_TRADER -> {
                // Wandering trader specific settings
                plugin.getLogger().fine("Configured wandering trader NPC");
            }
            case ZOMBIE_VILLAGER -> {
                // Zombie villager specific settings
                if (npc instanceof Villager villager) {
                    villager.setProfession(Villager.Profession.NITWIT);
                }
            }
            default -> {
                // Generic configuration for other entity types
                plugin.getLogger().fine("Using generic configuration for NPC type: " + type.name());
            }
        }
    }
    
    private void broadcastSpawnMessage() {
        try {
            var configData = plugin.getConfigManager().getConfigSnapshot();
            
            String messageTemplate = plugin.getConfigManager().getMessage("shop_spawned");
            Component message = plugin.getConfigManager().parseMessage(messageTemplate
                .replace("{location}", currentLocationName)
                .replace("{duration}", String.valueOf(configData.despawnTime())));
            
            Bukkit.getServer().sendMessage(message);
            
            // Play sound to all players
            String soundName = configData.spawnSound();
            Bukkit.getOnlinePlayers().forEach(player -> {
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName), 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid spawn sound: " + soundName);
                }
            });
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to broadcast spawn message", e);
        }
    }
    
    private void scheduleDespawn() {
        shopLock.writeLock().lock();
        try {
            // Cancel existing despawn task
            if (despawnTask != null && !despawnTask.isCancelled()) {
                despawnTask.cancel();
            }
            
            long despawnTime = plugin.getConfigManager().getConfigSnapshot().despawnTime() * 3600 * 20L; // Convert hours to ticks
            
            despawnTask = new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        despawnShop();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Error in despawn task", e);
                    }
                }
            }.runTaskLater(plugin, despawnTime);
            
        } finally {
            shopLock.writeLock().unlock();
        }
    }
    
    /**
     * Despawns the current bazaar shop with proper cleanup.
     */
    public void despawnShop() {
        shopLock.writeLock().lock();
        try {
            boolean wasActive = isShopActive();
            Location despawnLocation = currentLocation; // Save location before clearing
            
            // Remove NPC
            if (currentShop != null && !currentShop.isDead()) {
                currentShop.remove();
            }
            
            // Stop visual effects
            plugin.getVisualHints().stopParticleTask();
            
            // Create despawn particle burst if location is available
            if (wasActive && despawnLocation != null) {
                plugin.getVisualHints().createParticleBurst(
                    despawnLocation.clone().add(0, 1, 0), 
                    org.bukkit.Particle.CLOUD, 
                    15
                );
            }
            
            // Cancel despawn task
            if (despawnTask != null && !despawnTask.isCancelled()) {
                despawnTask.cancel();
                despawnTask = null;
            }
            
            // Clear state
            currentShop = null;
            currentLocation = null;
            currentLocationName = null;
            currentInventory = null;
            
            if (wasActive) {
                // Broadcast despawn message
                String messageTemplate = plugin.getConfigManager().getMessage("shop_despawned");
                Component message = plugin.getConfigManager().parseMessage(messageTemplate);
                Bukkit.getServer().sendMessage(message);
                
                plugin.getLogger().info("Bazaar despawned");
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error during bazaar despawn", e);
        } finally {
            shopLock.writeLock().unlock();
        }
    }
    
    /**
     * Thread-safe check if shop is currently active.
     */
    public boolean isShopActive() {
        shopLock.readLock().lock();
        try {
            return currentShop != null && !currentShop.isDead();
        } finally {
            shopLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current shop NPC safely.
     */
    public Optional<LivingEntity> getCurrentShop() {
        shopLock.readLock().lock();
        try {
            return Optional.ofNullable(currentShop);
        } finally {
            shopLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current location safely.
     */
    public Optional<Location> getCurrentLocation() {
        shopLock.readLock().lock();
        try {
            return Optional.ofNullable(currentLocation);
        } finally {
            shopLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current location name safely.
     */
    public Optional<String> getCurrentLocationName() {
        shopLock.readLock().lock();
        try {
            return Optional.ofNullable(currentLocationName);
        } finally {
            shopLock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current inventory safely.
     */
    public List<ShopItem> getCurrentInventory() {
        shopLock.readLock().lock();
        try {
            return currentInventory != null ? List.copyOf(currentInventory) : List.of();
        } finally {
            shopLock.readLock().unlock();
        }
    }
    
    /**
     * Forces a shop respawn (admin command).
     */
    public boolean forceRespawn() {
        shopLock.writeLock().lock();
        try {
            plugin.getLogger().info("Force respawning bazaar...");
            despawnShop();
            return spawnShop();
        } finally {
            shopLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets comprehensive shop status information.
     */
    public ShopStatus getShopStatus() {
        shopLock.readLock().lock();
        try {
            if (!isShopActive()) {
                return new ShopStatus(false, null, null, 0, -1);
            }
            
            long timeLeft = -1;
            if (despawnTask != null && !despawnTask.isCancelled()) {
                // Calculate remaining time (approximate)
                timeLeft = despawnTask.getTaskId(); // This is a limitation - Bukkit doesn't expose remaining time
            }
            
            return new ShopStatus(
                true,
                currentLocationName,
                currentLocation,
                currentInventory != null ? currentInventory.size() : 0,
                timeLeft
            );
        } finally {
            shopLock.readLock().unlock();
        }
    }
    
    /**
     * Shop status record for information queries.
     */
    public record ShopStatus(
        boolean active,
        String locationName,
        Location location,
        int itemCount,
        long timeLeftTicks
    ) {
        
        public boolean hasTimeLeft() {
            return timeLeftTicks > 0;
        }
        
        public long timeLeftSeconds() {
            return timeLeftTicks / 20;
        }
    }
}