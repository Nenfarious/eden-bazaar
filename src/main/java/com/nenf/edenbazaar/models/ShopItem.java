package com.nenf.edenbazaar.models;

import org.bukkit.inventory.ItemStack;

public class ShopItem {
    private final ItemStack itemStack;
    private final double price;
    private final String tier;
    
    public ShopItem(ItemStack itemStack, double price, String tier) {
        this.itemStack = itemStack;
        this.price = price;
        this.tier = tier;
    }
    
    public ItemStack getItemStack() {
        return itemStack;
    }
    
    public double getPrice() {
        return price;
    }
    
    public String getTier() {
        return tier;
    }
}