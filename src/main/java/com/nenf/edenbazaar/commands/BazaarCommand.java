package com.nenf.edenbazaar.commands;

import com.nenf.edenbazaar.EdenBazaar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Modern command executor with MiniMessage support and comprehensive validation.
 */
public class BazaarCommand implements CommandExecutor, TabCompleter {
    
    private final EdenBazaar plugin;
    
    // Valid command arguments for validation
    private static final List<String> VALID_COMMANDS = List.of(
        "spawn", "despawn", "setlocation", "additem", "reload", "info", "help"
    );
    
    private static final List<String> VALID_TIERS = List.of(
        "common", "rare", "legendary", "epic", "mythic"
    );
    
    // Define colors using MiniMessage color scheme
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#9D4EDD");    // Purple
    private static final TextColor ACCENT_COLOR = TextColor.fromHexString("#06FFA5");     // Cyan
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#51CF66");    // Green
    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B");      // Red
    private static final TextColor VALUE_COLOR = TextColor.fromHexString("#FFB3C6");      // Pink
    private static final TextColor NEUTRAL_COLOR = TextColor.fromHexString("#ADB5BD");    // Gray
    
    public BazaarCommand(EdenBazaar plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check
        if (!sender.hasPermission("edenbazaar.admin")) {
            Component message = plugin.getConfigManager().parseMessage(
                plugin.getConfigManager().getMessage("no_permission")
            );
            sender.sendMessage(message);
            return true;
        }
        
        // Show help if no arguments
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        // Validate command argument
        String subCommand = args[0].toLowerCase();
        if (!VALID_COMMANDS.contains(subCommand)) {
            Component errorMessage = Component.text("Unknown command: ", ERROR_COLOR)
                .append(Component.text(args[0], VALUE_COLOR));
            sender.sendMessage(errorMessage);
            sendHelp(sender);
            return true;
        }
        
        // Handle command with proper error catching
        try {
            CommandResult result = switch (subCommand) {
                case "spawn" -> handleSpawn(sender);
                case "despawn" -> handleDespawn(sender);
                case "setlocation" -> handleSetLocation(sender, args);
                case "additem" -> handleAddItem(sender, args);
                case "reload" -> handleReload(sender);
                case "info" -> handleInfo(sender);
                case "help" -> { sendHelp(sender); yield CommandResult.ofSuccess(); }
                default -> CommandResult.ofError("Unknown command: " + subCommand);
            };
            
            if (!result.success()) {
                Component errorMessage = Component.text(result.errorMessage(), ERROR_COLOR);
                sender.sendMessage(errorMessage);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error executing bazaar command", e);
            Component errorMessage = Component.text("An internal error occurred while executing the command.", ERROR_COLOR);
            sender.sendMessage(errorMessage);
        }
        
        return true;
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== EdenBazaar Commands ===", PRIMARY_COLOR).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        sender.sendMessage(Component.text("/bazaar spawn", ACCENT_COLOR).append(Component.text(" - Manually spawn the bazaar", NEUTRAL_COLOR)));
        sender.sendMessage(Component.text("/bazaar despawn", ACCENT_COLOR).append(Component.text(" - Despawn the current bazaar", NEUTRAL_COLOR)));
        sender.sendMessage(Component.text("/bazaar setlocation <name>", ACCENT_COLOR).append(Component.text(" - Add a spawn location", NEUTRAL_COLOR)));
        sender.sendMessage(Component.text("/bazaar additem <tier> <material> <min> <max> [weight]", ACCENT_COLOR).append(Component.text(" - Add item to loot pool", NEUTRAL_COLOR)));
        sender.sendMessage(Component.text("/bazaar reload", ACCENT_COLOR).append(Component.text(" - Reload configuration", NEUTRAL_COLOR)));
        sender.sendMessage(Component.text("/bazaar info", ACCENT_COLOR).append(Component.text(" - Show current bazaar info", NEUTRAL_COLOR)));
        sender.sendMessage(Component.text("/bazaar help", ACCENT_COLOR).append(Component.text(" - Show this help message", NEUTRAL_COLOR)));
    }
    
    private CommandResult handleSpawn(CommandSender sender) {
        if (plugin.getBazaarManager().isShopActive()) {
            return CommandResult.ofError("Bazaar is already active!");
        }
        
        boolean success = plugin.getBazaarManager().spawnShop();
        if (success) {
            Component successMessage = Component.text("Bazaar spawned successfully!", SUCCESS_COLOR);
            sender.sendMessage(successMessage);
            return CommandResult.ofSuccess();
        } else {
            return CommandResult.ofError("Failed to spawn bazaar. Check console for details.");
        }
    }
    
    private CommandResult handleDespawn(CommandSender sender) {
        if (!plugin.getBazaarManager().isShopActive()) {
            return CommandResult.ofError("No active bazaar to despawn!");
        }
        
        plugin.getBazaarManager().despawnShop();
        Component successMessage = Component.text("Bazaar despawned successfully!", SUCCESS_COLOR);
        sender.sendMessage(successMessage);
        return CommandResult.ofSuccess();
    }
    
    private CommandResult handleSetLocation(CommandSender sender, String[] args) {
        // Validate sender is player
        if (!(sender instanceof Player player)) {
            return CommandResult.ofError("This command can only be used by players!");
        }
        
        // Validate argument count
        if (args.length < 2) {
            return CommandResult.ofError("Usage: /bazaar setlocation <name>");
        }
        
        // Validate location name
        String locationName = args[1].trim();
        if (locationName.isEmpty() || locationName.length() > 32) {
            return CommandResult.ofError("Location name must be 1-32 characters long!");
        }
        
        // Validate location name characters
        if (!locationName.matches("^[a-zA-Z0-9_-]+$")) {
            return CommandResult.ofError("Location name can only contain letters, numbers, underscores, and hyphens!");
        }
        
        Location location = player.getLocation();
        
        // Validate world
        if (location.getWorld() == null) {
            return CommandResult.ofError("Invalid world!");
        }
        
        try {
            plugin.getConfigManager().addSpawnLocation(locationName, location);
            Component successMessage = Component.text("Location '", SUCCESS_COLOR)
                .append(Component.text(locationName, ACCENT_COLOR))
                .append(Component.text("' added successfully!", SUCCESS_COLOR));
            sender.sendMessage(successMessage);
            return CommandResult.ofSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add location: " + locationName, e);
            return CommandResult.ofError("Failed to save location. Check console for details.");
        }
    }
    
    private CommandResult handleAddItem(CommandSender sender, String[] args) {
        // Validate argument count
        if (args.length < 5) {
            return CommandResult.ofError("Usage: /bazaar additem <tier> <material> <min_price> <max_price> [weight]");
        }
        
        // Validate tier
        String tier = args[1].toLowerCase();
        if (!VALID_TIERS.contains(tier)) {
            return CommandResult.ofError("Invalid tier! Valid tiers: " + String.join(", ", VALID_TIERS));
        }
        
        // Validate material
        String materialName = args[2].toUpperCase();
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return CommandResult.ofError("Invalid material: " + args[2]);
        }
        
        // Validate material is an item
        if (!material.isItem()) {
            return CommandResult.ofError("Material " + material.name() + " is not a valid item!");
        }
        
        // Validate price arguments
        ValidationResult<Integer> minPriceResult = validateInteger(args[3], "minimum price", 1, 1_000_000);
        if (!minPriceResult.isValid()) {
            return CommandResult.ofError(minPriceResult.errorMessage());
        }
        
        ValidationResult<Integer> maxPriceResult = validateInteger(args[4], "maximum price", 1, 1_000_000);
        if (!maxPriceResult.isValid()) {
            return CommandResult.ofError(maxPriceResult.errorMessage());
        }
        
        int minPrice = minPriceResult.value();
        int maxPrice = maxPriceResult.value();
        
        // Validate price relationship
        if (minPrice > maxPrice) {
            return CommandResult.ofError("Minimum price cannot be greater than maximum price!");
        }
        
        // Validate weight (optional)
        int weight = 1;
        if (args.length > 5) {
            ValidationResult<Integer> weightResult = validateInteger(args[5], "weight", 1, 1000);
            if (!weightResult.isValid()) {
                return CommandResult.ofError(weightResult.errorMessage());
            }
            weight = weightResult.value();
        }
        
        try {
            plugin.getConfigManager().addLootItem(tier, material, minPrice, maxPrice, weight);
            Component successMessage = Component.text("Item ", SUCCESS_COLOR)
                .append(Component.text(material.name(), VALUE_COLOR))
                .append(Component.text(" added to tier '", SUCCESS_COLOR))
                .append(Component.text(tier, PRIMARY_COLOR))
                .append(Component.text("' successfully!", SUCCESS_COLOR));
            sender.sendMessage(successMessage);
            return CommandResult.ofSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add loot item", e);
            return CommandResult.ofError("Failed to add item. Check console for details.");
        }
    }
    
    private CommandResult handleReload(CommandSender sender) {
        try {
            plugin.getConfigManager().loadConfigs();
            Component successMessage = Component.text("Configuration reloaded successfully!", SUCCESS_COLOR);
            sender.sendMessage(successMessage);
            return CommandResult.ofSuccess();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload config", e);
            return CommandResult.ofError("Failed to reload configuration. Check console for details.");
        }
    }
    
    private CommandResult handleInfo(CommandSender sender) {
        if (!plugin.getBazaarManager().isShopActive()) {
            return CommandResult.ofError("No active bazaar!");
        }
        
        var status = plugin.getBazaarManager().getShopStatus();
        
        sender.sendMessage(Component.text("=== Bazaar Information ===", PRIMARY_COLOR).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        sender.sendMessage(Component.text("Location: ", PRIMARY_COLOR).append(Component.text(status.locationName(), NamedTextColor.WHITE)));
        
        if (status.location() != null) {
            sender.sendMessage(Component.text("Coordinates: ", PRIMARY_COLOR).append(Component.text(
                String.format("%.1f, %.1f, %.1f", status.location().getX(), status.location().getY(), status.location().getZ()), 
                NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("World: ", PRIMARY_COLOR).append(Component.text(status.location().getWorld().getName(), NamedTextColor.WHITE)));
        }
        
        sender.sendMessage(Component.text("Items: ", PRIMARY_COLOR).append(Component.text(String.valueOf(status.itemCount()), NamedTextColor.WHITE)));
        
        if (status.hasTimeLeft()) {
            long minutes = status.timeLeftSeconds() / 60;
            sender.sendMessage(Component.text("Time left: ", PRIMARY_COLOR).append(Component.text(minutes + " minutes", NamedTextColor.WHITE)));
        }
        
        return CommandResult.ofSuccess();
    }
    
    /**
     * Validates an integer argument with bounds checking.
     */
    private ValidationResult<Integer> validateInteger(String input, String fieldName, int min, int max) {
        try {
            int value = Integer.parseInt(input);
            if (value < min || value > max) {
                return ValidationResult.ofError("Invalid " + fieldName + ": must be between " + min + " and " + max);
            }
            return ValidationResult.ofSuccess(value);
        } catch (NumberFormatException e) {
            return ValidationResult.ofError("Invalid " + fieldName + ": must be a valid number");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("edenbazaar.admin")) {
            return List.of();
        }
        
        List<String> completions = new ArrayList<>();
        
        switch (args.length) {
            case 1 -> completions.addAll(VALID_COMMANDS);
            case 2 -> {
                if ("additem".equalsIgnoreCase(args[0])) {
                    completions.addAll(VALID_TIERS);
                }
            }
            case 3 -> {
                if ("additem".equalsIgnoreCase(args[0])) {
                    // Add common materials
                    completions.addAll(List.of("DIAMOND", "EMERALD", "IRON_INGOT", "GOLD_INGOT", "ENCHANTED_BOOK", "NETHERITE_INGOT"));
                }
            }
            case 4, 5 -> {
                if ("additem".equalsIgnoreCase(args[0])) {
                    completions.addAll(List.of("1", "10", "100", "1000"));
                }
            }
            case 6 -> {
                if ("additem".equalsIgnoreCase(args[0])) {
                    completions.addAll(List.of("1", "5", "10", "25", "50"));
                }
            }
        }
        
        // Filter completions based on current input
        String currentArg = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(completion -> completion.toLowerCase().startsWith(currentArg))
            .toList();
    }
    
    /**
     * Command execution result record.
     */
    private record CommandResult(boolean success, String errorMessage) {
        
        static CommandResult ofSuccess() {
            return new CommandResult(true, null);
        }
        
        static CommandResult ofError(String message) {
            return new CommandResult(false, message);
        }
    }
    
    /**
     * Generic validation result record.
     */
    private record ValidationResult<T>(boolean isValid, T value, String errorMessage) {
        
        static <T> ValidationResult<T> ofSuccess(T value) {
            return new ValidationResult<>(true, value, null);
        }
        
        static <T> ValidationResult<T> ofError(String message) {
            return new ValidationResult<>(false, null, message);
        }
    }
}