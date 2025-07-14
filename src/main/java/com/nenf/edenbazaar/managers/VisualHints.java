package com.nenf.edenbazaar.managers;

import com.nenf.edenbazaar.EdenBazaar;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.logging.Level;

public class VisualHints {
    
    private final EdenBazaar plugin;
    private BukkitTask particleTask;
    
    public VisualHints(EdenBazaar plugin) {
        this.plugin = plugin;
    }
    
    public void startParticleTask(Location shopLocation) {
        stopParticleTask(); // Stop any existing task
        
        // Read configuration values properly
        var configData = plugin.getConfigManager().getConfigSnapshot();
        
        if (!configData.particlesEnabled()) {
            plugin.getLogger().fine("Particles are disabled in configuration");
            return;
        }
        
        // Enhanced configuration reading
        double particleRange = configData.particleRange();
        String particleType = configData.particleType();
        
        // Additional configuration from main config for enhanced features
        var config = plugin.getConfigManager().getConfig();
        int updateInterval = config.getInt("settings.particles.update_interval", 20); // ticks
        boolean showTrails = config.getBoolean("settings.particles.show_trails", true);
        double trailRange = config.getDouble("settings.particles.trail_range", 50.0);
        int particleCount = config.getInt("settings.particles.count", 16);
        double circleRadius = config.getDouble("settings.particles.circle_radius", 0.5);
        double verticalMovement = config.getDouble("settings.particles.vertical_movement", 0.2);
        
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (shopLocation == null || shopLocation.getWorld() == null) {
                    cancel();
                    return;
                }
                
                // Check if shop is still active
                if (!plugin.getBazaarManager().isShopActive()) {
                    cancel();
                    return;
                }
                
                // Spawn particles above the shop
                Location particleLocation = shopLocation.clone().add(0, 2, 0);
                
                try {
                    Particle particle = Particle.valueOf(particleType);
                    
                    // Get players within range first
                    List<Player> nearbyPlayers = shopLocation.getWorld().getPlayers().stream()
                        .filter(player -> player.getLocation().distance(shopLocation) <= particleRange)
                        .toList();
                    
                    // Only create particles if there are players nearby
                    if (!nearbyPlayers.isEmpty()) {
                        // Create enhanced circular particle effect
                        for (int i = 0; i < particleCount; i++) {
                            double angle = (i * Math.PI * 2) / particleCount;
                            double x = Math.cos(angle) * circleRadius;
                            double z = Math.sin(angle) * circleRadius;
                            
                            // Add vertical movement based on time
                            double verticalOffset = Math.sin(System.currentTimeMillis() * 0.001) * verticalMovement;
                            Location spawnLoc = particleLocation.clone().add(x, verticalOffset, z);
                            
                            // Spawn particle for all nearby players
                            for (Player nearbyPlayer : nearbyPlayers) {
                                nearbyPlayer.spawnParticle(particle, spawnLoc, 1, 0, 0, 0, 0);
                            }
                        }
                    }
                    
                    // Show particle trail to players within trail range (if enabled)
                    if (showTrails) {
                        List<Player> trailPlayers = shopLocation.getWorld().getPlayers().stream()
                            .filter(player -> {
                                double distanceToShop = player.getLocation().distance(shopLocation);
                                return distanceToShop <= trailRange && distanceToShop > 10.0;
                            })
                            .toList();
                        
                        for (Player player : trailPlayers) {
                            showParticleTrail(player, shopLocation);
                        }
                    }
                    
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid particle type: " + particleType + ". Disabling particle effects.");
                    cancel();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in particle task", e);
                }
            }
        }.runTaskTimer(plugin, 0L, updateInterval);
        
        plugin.getLogger().fine("Started particle effects for bazaar at " + shopLocation + " with range " + particleRange);
    }
    
    private void showParticleTrail(Player player, Location shopLocation) {
        try {
            Location playerLoc = player.getLocation();
            Location direction = shopLocation.clone().subtract(playerLoc);
            
            // Normalize and scale the direction vector
            double distance = direction.length();
            if (distance > 0) {
                direction.multiply(1.0 / distance); // Normalize
                direction.multiply(3.0); // Scale for visibility
                
                // Create a subtle trail pointing towards the shop
                for (int i = 1; i <= 3; i++) {
                    Location trailLoc = playerLoc.clone()
                        .add(direction.clone().multiply(i))
                        .add(0, 1.5, 0); // Slightly above player eye level
                    
                    // Use different particle for trails
                    player.spawnParticle(Particle.HAPPY_VILLAGER, trailLoc, 1, 0.1, 0.1, 0.1, 0);
                }
            }
        } catch (Exception e) {
            // Silently ignore trail errors to avoid spam
            plugin.getLogger().fine("Error creating particle trail for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    public void stopParticleTask() {
        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
            particleTask = null;
            plugin.getLogger().fine("Stopped particle effects for bazaar");
        }
    }
    
    /**
     * Creates a temporary particle burst at the specified location.
     * Useful for special events like purchases or shop spawn/despawn.
     */
    public void createParticleBurst(Location location, Particle particleType, int count) {
        if (!plugin.getConfigManager().getConfigSnapshot().particlesEnabled()) {
            return;
        }
        
        try {
            double burstRange = plugin.getConfigManager().getConfigSnapshot().particleRange();
            
            // Only show burst to players within range
            List<Player> nearbyPlayers = location.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distance(location) <= burstRange)
                .toList();
            
            for (Player player : nearbyPlayers) {
                player.spawnParticle(particleType, location, count, 0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error creating particle burst", e);
        }
    }
}