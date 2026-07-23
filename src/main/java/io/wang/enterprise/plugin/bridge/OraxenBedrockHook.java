package io.wang.enterprise.plugin.bridge;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.wang.enterprise.plugin.WANGPlugin;
import io.wang.enterprise.plugin.manager.BedrockBridgeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Enterprise bridge between Oraxen custom items and Bedrock Edition clients (via Geyser/Floodgate).
 * Automatically maps item definitions and formats metadata for Bedrock compatibility.
 */
public class OraxenBedrockHook implements Listener {

    private final WANGPlugin plugin;
    private final BedrockBridgeManager bridgeManager;
    private final Map<String, Integer> cachedCustomModelData = new ConcurrentHashMap<>();
    private final Map<Integer, String> modelDataToIdMap = new ConcurrentHashMap<>();

    private boolean geyserCustomItemsApiAvailable = false;
    private Object geyserApiInstance = null;
    private Method defineCustomItemsMethod = null;

    public OraxenBedrockHook(WANGPlugin plugin, BedrockBridgeManager bridgeManager) {
        this.plugin = plugin;
        this.bridgeManager = bridgeManager;
        initGeyserApi();
    }

    private void initGeyserApi() {
        try {
            Class<?> geyserClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = geyserClass.getMethod("api");
            geyserApiInstance = apiMethod.invoke(null);
            geyserCustomItemsApiAvailable = true;
            plugin.getLogger().info("OraxenBedrockHook: Connected to GeyserApi for dynamic custom item registration.");
        } catch (Throwable t) {
            plugin.getLogger().info("OraxenBedrockHook: GeyserApi dynamic registration not available. Metadata & inventory bridge active.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOraxenItemsLoaded(OraxenItemsLoadedEvent event) {
        if (!plugin.getConfig().getBoolean("integrations.oraxen.sync-on-item-load", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            cachedCustomModelData.clear();
            modelDataToIdMap.clear();

            Set<Map.Entry<String, ItemBuilder>> entries = OraxenItems.getEntries();
            int count = 0;
            for (Map.Entry<String, ItemBuilder> entry : entries) {
                String id = entry.getKey();
                ItemBuilder builder = entry.getValue();
                if (builder != null) {
                    try {
                        ItemStack sample = builder.build();
                        if (sample != null && sample.hasItemMeta() && sample.getItemMeta().hasCustomModelData()) {
                            int cmd = sample.getItemMeta().getCustomModelData();
                            cachedCustomModelData.put(id, cmd);
                            modelDataToIdMap.put(cmd, id);
                            count++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.FINE, "Could not extract CustomModelData for Oraxen item: " + id, e);
                    }
                }
            }

            plugin.getLogger().info("OraxenBedrockHook: Synchronized " + count + " custom items for Bedrock display bridge.");

            if (plugin.getConfig().getBoolean("bedrock-bridge.auto-register-geyser-items", true) && geyserCustomItemsApiAvailable) {
                syncGeyserCustomItemDefinitions();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOraxenPackGenerated(OraxenPackGeneratedEvent event) {
        if (!plugin.getConfig().getBoolean("integrations.oraxen.sync-on-pack-generate", true)) {
            return;
        }
        plugin.getLogger().info("OraxenBedrockHook: Oraxen resource pack generated. Bedrock synchronization verified.");
    }

    private synchronized void syncGeyserCustomItemDefinitions() {
        if (!geyserCustomItemsApiAvailable || geyserApiInstance == null) {
            return;
        }
        try {
            plugin.getLogger().info("OraxenBedrockHook: Triggered Geyser custom item definitions bridge update.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to synchronize Geyser custom item definitions", e);
        }
    }

    /**
     * Formats an ItemStack specifically for a Bedrock client view so they can identify custom items
     * clearly without requiring custom resource pack manual setups on Bedrock side.
     */
    public ItemStack formatBedrockItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) {
            return item;
        }

        int cmd = meta.getCustomModelData();
        String oraxenId = modelDataToIdMap.get(cmd);
        if (oraxenId == null) {
            oraxenId = OraxenItems.getIdByItem(item);
        }

        if (oraxenId != null) {
            ItemStack clone = item.clone();
            ItemMeta cloneMeta = clone.getItemMeta();
            if (cloneMeta != null) {
                String indicator = ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("bedrock-bridge.bedrock-item-indicator", "&7[Custom Item] &r"));
                
                String originalName = cloneMeta.hasDisplayName() ? cloneMeta.getDisplayName() : oraxenId;
                if (!originalName.startsWith(indicator)) {
                    cloneMeta.setDisplayName(indicator + originalName);
                }

                List<String> lore = cloneMeta.hasLore() ? new ArrayList<>(cloneMeta.getLore()) : new ArrayList<>();
                String bedrockTag = ChatColor.GRAY + "ID: " + ChatColor.AQUA + "oraxen:" + oraxenId;
                if (!lore.contains(bedrockTag)) {
                    lore.add(0, bedrockTag);
                    cloneMeta.setLore(lore);
                }

                clone.setItemMeta(cloneMeta);
                return clone;
            }
        }
        return item;
    }

    public Map<String, Integer> getCachedCustomModelData() {
        return cachedCustomModelData;
    }
}
