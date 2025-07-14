package com.nenf.edenbazaar.models;

import org.bukkit.Location;

public class SpawnLocation {
    private final Location location;
    private final String name;
    
    public SpawnLocation(Location location, String name) {
        this.location = location;
        this.name = name;
    }
    
    public Location getLocation() {
        return location;
    }
    
    public String getName() {
        return name;
    }
}