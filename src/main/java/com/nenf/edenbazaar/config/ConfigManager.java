package com.nenf.edenbazaar.config;

import com.nenf.edenbazaar.EdenBazaar;
import com.nenf.edenbazaar.models.LootItem;
import com.nenf.edenbazaar.models.SpawnLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modern configuration manager with MiniMessage support, thread safety, validation, and error handling.
 * Follows contemporary Java 21 patterns and configuration best practices.
 */
public class ConfigManager {
    
    private final EdenBazaar plugin;
    private final Logger logger;
    private final ReadWriteLock configLock = new ReentrantReadWriteLock();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    // Configuration files
    private final ConfigFiles configFiles;
    private volatile ConfigData currentConfig;
    
    /**
     * Immutable configuration file references.
     */
    private record ConfigFiles(
        File config,
        File locations,
        File loot,
        File gui,
        File messages
    ) {}
    
    /**
     * Immutable configuration data container.
     */
    public record ConfigData(
        // Basic settings
        String prefix,
        boolean debugMode,
        long spawnInterval,
        int despawnTime,
        int maxShopItems,
        
        // Sounds and effects
        String spawnSound,
        String purchaseSound,
        boolean particlesEnabled,
        String particleType,
        double particleRange,
        
        // Economy settings
        boolean useVault,
        String currencyName,
        String currencySymbol,
        
        // Cached collections
        List<SpawnLocation> spawnLocations,
        Map<String, List<LootItem>> lootPools,
        Map<String, String> messages
    ) {
        
        /**
         * Creates a builder for ConfigData.
         */
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String prefix = "<color:#ADB5BD>[EdenBazaar]</color> ";
            private boolean debugMode = false;
            private long spawnInterval = 43200L;
            private int despawnTime = 6;
            private int maxShopItems = 5;
            private String spawnSound = "BLOCK_NOTE_BLOCK_XYLOPHONE";
            private String purchaseSound = "ENTITY_EXPERIENCE_ORB_PICKUP";
            private boolean particlesEnabled = true;
            private String particleType = "END_ROD";
            private double particleRange = 100.0;
            private boolean useVault = true;
            private String currencyName = "coins";
            private String currencySymbol = "⚡";
            private List<SpawnLocation> spawnLocations = List.of();
            private Map<String, List<LootItem>> lootPools = Map.of();
            private Map<String, String> messages = Map.of();
            
            public Builder prefix(String prefix) { this.prefix = prefix; return this; }
            public Builder debugMode(boolean debugMode) { this.debugMode = debugMode; return this; }
            public Builder spawnInterval(long spawnInterval) { this.spawnInterval = spawnInterval; return this; }
            public Builder despawnTime(int despawnTime) { this.despawnTime = despawnTime; return this; }
            public Builder maxShopItems(int maxShopItems) { this.maxShopItems = maxShopItems; return this; }
            public Builder spawnSound(String spawnSound) { this.spawnSound = spawnSound; return this; }
            public Builder purchaseSound(String purchaseSound) { this.purchaseSound = purchaseSound; return this; }
            public Builder particlesEnabled(boolean particlesEnabled) { this.particlesEnabled = particlesEnabled; return this; }
            public Builder particleType(String particleType) { this.particleType = particleType; return this; }
            public Builder particleRange(double particleRange) { this.particleRange = particleRange; return this; }
            public Builder useVault(boolean useVault) { this.useVault = useVault; return this; }
            public Builder currencyName(String currencyName) { this.currencyName = currencyName; return this; }
            public Builder currencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; return this; }
            public Builder spawnLocations(List<SpawnLocation> spawnLocations) { this.spawnLocations = List.copyOf(spawnLocations); return this; }
            public Builder lootPools(Map<String, List<LootItem>> lootPools) { this.lootPools = Map.copyOf(lootPools); return this; }
            public Builder messages(Map<String, String> messages) { this.messages = Map.copyOf(messages); return this; }
            
            public ConfigData build() {
                return new ConfigData(
                    prefix, debugMode, spawnInterval, despawnTime, maxShopItems,
                    spawnSound, purchaseSound, particlesEnabled, particleType, particleRange,
                    useVault, currencyName, currencySymbol,
                    spawnLocations, lootPools, messages
                );
            }
        }
    }
    
    public ConfigManager(EdenBazaar plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFiles = initializeConfigFiles();
    }
    
    private ConfigFiles initializeConfigFiles() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.severe("Failed to create plugin data folder!");
        }
        
        return new ConfigFiles(
            new File(dataFolder, "config.yml"),
            new File(dataFolder, "locations.yml"),
            new File(dataFolder, "loot.yml"),
            new File(dataFolder, "gui.yml"),
            new File(dataFolder, "messages.yml")
        );
    }
    
    /**
     * Loads all configuration files with proper error handling and validation.
     */
    public void loadConfigs() {
        configLock.writeLock().lock();
        try {
            saveDefaultConfigs();
            
            ValidationResult validation = loadAndValidateConfigurations();
            validation.logResults(logger);
            
            if (!validation.isValid()) {
                logger.severe("Configuration validation failed! Using default values where possible.");
                currentConfig = ConfigData.builder().build(); // Use defaults
                return;
            }
            
            logger.info("Configuration loaded successfully" + 
                (validation.warnings().isEmpty() ? "" : " with " + validation.warnings().size() + " warnings"));
                
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load configuration", e);
            currentConfig = ConfigData.builder().build(); // Fallback to defaults
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    private void saveDefaultConfigs() {
        try {
            if (!configFiles.config().exists()) plugin.saveResource("config.yml", false);
            if (!configFiles.locations().exists()) plugin.saveResource("locations.yml", false);
            if (!configFiles.loot().exists()) plugin.saveResource("loot.yml", false);
            if (!configFiles.gui().exists()) plugin.saveResource("gui.yml", false);
            if (!configFiles.messages().exists()) plugin.saveResource("messages.yml", false);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to save default config files", e);
        }
    }
    
    private ValidationResult loadAndValidateConfigurations() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFiles.config());
            FileConfiguration locationsConfig = YamlConfiguration.loadConfiguration(configFiles.locations());
            FileConfiguration lootConfig = YamlConfiguration.loadConfiguration(configFiles.loot());
            FileConfiguration guiConfig = YamlConfiguration.loadConfiguration(configFiles.gui());
            FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(configFiles.messages());
            
            ConfigData.Builder builder = ConfigData.builder();
            
            // Load and validate basic settings
            loadBasicSettings(config, builder, errors, warnings);
            
            // Load collections
            List<SpawnLocation> locations = loadSpawnLocations(locationsConfig, errors, warnings);
            Map<String, List<LootItem>> lootPools = loadLootPools(lootConfig, errors, warnings);
            Map<String, String> messages = loadMessages(messagesConfig, errors, warnings);
            
            builder.spawnLocations(locations)
                   .lootPools(lootPools)
                   .messages(messages);
            
            currentConfig = builder.build();
            
            return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
            
        } catch (Exception e) {
            errors.add("Failed to load configuration files: " + e.getMessage());
            return new ValidationResult(false, errors, warnings);
        }
    }
    
    private void loadBasicSettings(FileConfiguration config, ConfigData.Builder builder, 
                                 List<String> errors, List<String> warnings) {
        // Prefix validation - now supports MiniMessage format
        String prefix = config.getString("settings.prefix", "<color:#ADB5BD>[EdenBazaar]</color> ");
        if (prefix.trim().isEmpty()) {
            errors.add("Plugin prefix cannot be empty");
            prefix = "<color:#ADB5BD>[EdenBazaar]</color> ";
        }
        builder.prefix(prefix); // Store as MiniMessage format
        
        // Spawn interval validation
        long spawnInterval = config.getLong("settings.spawn_interval", 43200L);
        if (spawnInterval < 60) {
            warnings.add("Spawn interval is very low (" + spawnInterval + "s), consider increasing it");
            spawnInterval = Math.max(60, spawnInterval);
        }
        builder.spawnInterval(spawnInterval);
        
        // Despawn time validation  
        int despawnTime = config.getInt("settings.despawn_time", 6);
        if (despawnTime < 1) {
            errors.add("Despawn time must be at least 1 hour, got: " + despawnTime);
            despawnTime = 6;
        }
        builder.despawnTime(despawnTime);
        
        // Max shop items validation
        int maxShopItems = config.getInt("settings.max_shop_items", 5);
        if (maxShopItems < 1 || maxShopItems > 54) {
            warnings.add("Max shop items should be between 1 and 54, got: " + maxShopItems);
            maxShopItems = Math.clamp(maxShopItems, 1, 54);
        }
        builder.maxShopItems(maxShopItems);
        
        // Other settings
        builder.debugMode(config.getBoolean("settings.debug", false))
               .spawnSound(config.getString("settings.spawn_sound", "BLOCK_NOTE_BLOCK_XYLOPHONE"))
               .purchaseSound(config.getString("settings.purchase_sound", "ENTITY_EXPERIENCE_ORB_PICKUP"))
               .particlesEnabled(config.getBoolean("settings.particles.enabled", true))
               .particleType(config.getString("settings.particles.type", "END_ROD"))
               .particleRange(config.getDouble("settings.particles.range", 100.0))
               .useVault(config.getBoolean("economy.use_vault", true))
               .currencyName(config.getString("economy.currency_name", "coins"))
               .currencySymbol(config.getString("economy.currency_symbol", "⚡"));
    }
    
    private List<SpawnLocation> loadSpawnLocations(FileConfiguration config, 
                                                 List<String> errors, List<String> warnings) {
        List<SpawnLocation> locations = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("spawn_points");
        
        if (section == null) {
            warnings.add("No spawn locations configured");
            return List.of();
        }
        
        for (String key : section.getKeys(false)) {
            try {
                ConfigurationSection locationSection = section.getConfigurationSection(key);
                if (locationSection == null) continue;
                
                String worldName = locationSection.getString("world");
                if (worldName == null) {
                    errors.add("Location '" + key + "' missing world name");
                    continue;
                }
                
                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    warnings.add("World '" + worldName + "' for location '" + key + "' not found");
                    continue;
                }
                
                double x = locationSection.getDouble("x");
                double y = locationSection.getDouble("y");
                double z = locationSection.getDouble("z");
                float yaw = (float) locationSection.getDouble("yaw", 0);
                float pitch = (float) locationSection.getDouble("pitch", 0);
                String name = locationSection.getString("name", key);
                
                Location location = new Location(world, x, y, z, yaw, pitch);
                locations.add(new SpawnLocation(location, name));
                
            } catch (Exception e) {
                errors.add("Failed to load location '" + key + "': " + e.getMessage());
            }
        }
        
        return List.copyOf(locations);
    }
    
    private Map<String, List<LootItem>> loadLootPools(FileConfiguration config,
                                                    List<String> errors, List<String> warnings) {
        Map<String, List<LootItem>> lootPools = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("loot_pools");
        
        if (section == null) {
            errors.add("No loot pools configured");
            return Map.of();
        }
        
        for (String tier : section.getKeys(false)) {
            List<LootItem> items = new ArrayList<>();
            ConfigurationSection tierSection = section.getConfigurationSection(tier);
            
            if (tierSection == null) continue;
            
            for (String key : tierSection.getKeys(false)) {
                try {
                    ConfigurationSection itemSection = tierSection.getConfigurationSection(key);
                    if (itemSection == null) continue;
                    
                    String materialName = itemSection.getString("item");
                    if (materialName == null) {
                        errors.add("Item '" + key + "' in tier '" + tier + "' missing material");
                        continue;
                    }
                    
                    Material material = Material.valueOf(materialName.toUpperCase());
                    List<Integer> priceRange = itemSection.getIntegerList("price_range");
                    int weight = itemSection.getInt("weight", 1);
                    
                    int minPrice = priceRange.size() > 0 ? priceRange.get(0) : 10;
                    int maxPrice = priceRange.size() > 1 ? priceRange.get(1) : 100;
                    
                    LootItem lootItem = new LootItem(material, minPrice, maxPrice, weight);
                    items.add(lootItem);
                    
                } catch (IllegalArgumentException e) {
                    errors.add("Invalid material or values for item '" + key + "' in tier '" + tier + "': " + e.getMessage());
                } catch (Exception e) {
                    errors.add("Failed to load item '" + key + "' in tier '" + tier + "': " + e.getMessage());
                }
            }
            
            lootPools.put(tier, List.copyOf(items));
        }
        
        return Map.copyOf(lootPools);
    }
    
    private Map<String, String> loadMessages(FileConfiguration config,
                                           List<String> errors, List<String> warnings) {
        Map<String, String> messages = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages");
        
        if (section == null) {
            warnings.add("No messages configured, using defaults");
            return getDefaultMessages();
        }
        
        for (String key : section.getKeys(false)) {
            String message = section.getString(key);
            if (message != null) {
                // Store as MiniMessage format (no need to translate anymore)
                messages.put(key, message);
            }
        }
        
        // Ensure required messages exist
        Map<String, String> defaults = getDefaultMessages();
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            messages.putIfAbsent(entry.getKey(), entry.getValue());
        }
        
        return Map.copyOf(messages);
    }
    
    private Map<String, String> getDefaultMessages() {
        return Map.of(
            "shop_spawned", "<bold><color:#9D4EDD>[MOBILE BAZAAR]</color></bold> <white>Has appeared!</white>",
            "shop_despawned", "<bold><color:#9D4EDD>[MOBILE BAZAAR]</color></bold> <white>Has vanished!</white>",
            "purchase_success", "<color:#51CF66>Successfully purchased {item} for {price}!</color>",
            "not_enough_money", "<color:#FF6B6B>You don't have enough money!</color>",
            "inventory_full", "<color:#FF6B6B>Your inventory is full!</color>",
            "no_permission", "<color:#FF6B6B>You don't have permission!</color>"
        );
    }
    
    // Thread-safe getters
    public ConfigData getConfigSnapshot() {
        configLock.readLock().lock();
        try {
            return currentConfig;
        } finally {
            configLock.readLock().unlock();
        }
    }
    
    // Legacy support methods
    public FileConfiguration getConfig() {
        return YamlConfiguration.loadConfiguration(configFiles.config());
    }
    
    public FileConfiguration getGuiConfig() {
        return YamlConfiguration.loadConfiguration(configFiles.gui());
    }
    
    public List<SpawnLocation> getSpawnLocations() {
        return getConfigSnapshot().spawnLocations();
    }
    
    public Map<String, List<LootItem>> getLootPools() {
        return getConfigSnapshot().lootPools();
    }
    
    // Modern MiniMessage methods
    public String getMessage(String key) {
        Map<String, String> messages = getConfigSnapshot().messages();
        return messages.getOrDefault(key, "<color:#FF6B6B>Message not found: " + key + "</color>");
    }
    
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        
        // Modern replacement with validation
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = replacements[i];
            String replacement = replacements[i + 1];
            if (placeholder != null && replacement != null) {
                message = message.replace(placeholder, replacement);
            }
        }
        
        return message;
    }
    
    /**
     * Converts a MiniMessage string to a Component.
     */
    public Component parseMessage(String miniMessageString) {
        try {
            return miniMessage.deserialize(miniMessageString);
        } catch (Exception e) {
            logger.warning("Failed to parse MiniMessage: " + miniMessageString + " - " + e.getMessage());
            return Component.text(miniMessageString);
        }
    }
    
    /**
     * Gets a message as a Component with placeholders.
     */
    public Component getMessageComponent(String key, TagResolver... placeholders) {
        String messageString = getMessage(key);
        try {
            return miniMessage.deserialize(messageString, placeholders);
        } catch (Exception e) {
            logger.warning("Failed to parse message component for key: " + key + " - " + e.getMessage());
            return Component.text(messageString);
        }
    }
    
    /**
     * Helper method to create placeholder resolvers.
     */
    public static TagResolver placeholder(String key, String value) {
        return Placeholder.unparsed(key, value);
    }
    
    /**
     * Helper method to create component placeholder resolvers.
     */
    public static TagResolver placeholder(String key, Component value) {
        return Placeholder.component(key, value);
    }
    
    // File modification methods with proper error handling
    public void addSpawnLocation(String name, Location location) {
        configLock.writeLock().lock();
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFiles.locations());
            ConfigurationSection section = config.getConfigurationSection("spawn_points");
            if (section == null) {
                section = config.createSection("spawn_points");
            }
            
            ConfigurationSection locationSection = section.createSection(name);
            locationSection.set("world", location.getWorld().getName());
            locationSection.set("x", location.getX());
            locationSection.set("y", location.getY());
            locationSection.set("z", location.getZ());
            locationSection.set("yaw", location.getYaw());
            locationSection.set("pitch", location.getPitch());
            locationSection.set("name", name);
            
            config.save(configFiles.locations());
            
            // Reload configuration
            loadConfigs();
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save location: " + name, e);
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    public void addLootItem(String tier, Material material, int minPrice, int maxPrice, int weight) {
        configLock.writeLock().lock();
        try {
            // Validate inputs
            new LootItem(material, minPrice, maxPrice, weight); // This will throw if invalid
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFiles.loot());
            ConfigurationSection section = config.getConfigurationSection("loot_pools." + tier);
            if (section == null) {
                section = config.createSection("loot_pools." + tier);
            }
            
            String itemKey = material.name().toLowerCase();
            ConfigurationSection itemSection = section.createSection(itemKey);
            itemSection.set("item", material.name());
            itemSection.set("price_range", List.of(minPrice, maxPrice));
            itemSection.set("weight", weight);
            
            config.save(configFiles.loot());
            
            // Reload configuration
            loadConfigs();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not save loot item: " + material.name(), e);
        } finally {
            configLock.writeLock().unlock();
        }
    }
    
    /**
     * Validation result record.
     */
    private record ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
        
        void logResults(Logger logger) {
            warnings.forEach(warning -> logger.warning("Config warning: " + warning));
            errors.forEach(error -> logger.severe("Config error: " + error));
        }
    }
}