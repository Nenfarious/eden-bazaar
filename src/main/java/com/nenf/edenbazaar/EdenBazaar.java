package com.nenf.edenbazaar;

import com.nenf.edenbazaar.commands.BazaarCommand;
import com.nenf.edenbazaar.config.ConfigManager;
import com.nenf.edenbazaar.listeners.BazaarListener;
import com.nenf.edenbazaar.managers.BazaarManager;
import com.nenf.edenbazaar.managers.EconomyManager;
import com.nenf.edenbazaar.managers.LootGenerator;
import com.nenf.edenbazaar.managers.VisualHints;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class EdenBazaar extends JavaPlugin {
    
    private static EdenBazaar instance;
    private ConfigManager configManager;
    private BazaarManager bazaarManager;
    private LootGenerator lootGenerator;
    private VisualHints visualHints;
    private EconomyManager economyManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        try {
            // Initialize config manager first
            configManager = new ConfigManager(this);
            
            // Load configurations immediately
            configManager.loadConfigs();
            
            // Initialize other managers after config is loaded
            economyManager = new EconomyManager(this);
            lootGenerator = new LootGenerator(this);
            visualHints = new VisualHints(this);
            bazaarManager = new BazaarManager(this);
            
            // Register listeners
            getServer().getPluginManager().registerEvents(new BazaarListener(this), this);
            
            // Register commands
            getCommand("bazaar").setExecutor(new BazaarCommand(this));
            
            // Setup economy
            economyManager.setupEconomy();
            
            // Start bazaar scheduler
            bazaarManager.startScheduler();
            
            getLogger().info("EdenBazaar has been enabled successfully!");
            getLogger().info("Economy System: " + economyManager.getEconomyType().getDisplayName());
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable EdenBazaar!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        try {
            getLogger().info("Shutting down EdenBazaar...");
            
            // Stop all schedulers and clean up resources
            if (bazaarManager != null) {
                bazaarManager.despawnShop();
                bazaarManager.stopScheduler();
            }
            
            if (visualHints != null) {
                visualHints.stopParticleTask();
            }
            
            if (economyManager != null) {
                economyManager.shutdown();
            }
            
            // Clear static reference
            instance = null;
            
            getLogger().info("EdenBazaar has been disabled successfully!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin shutdown", e);
        }
    }
    
    public static EdenBazaar getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public BazaarManager getBazaarManager() {
        return bazaarManager;
    }
    
    public LootGenerator getLootGenerator() {
        return lootGenerator;
    }
    
    public VisualHints getVisualHints() {
        return visualHints;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}