package io.wang.enterprise.plugin.manager;

import io.wang.enterprise.plugin.WANGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages detection of Bedrock players (via Floodgate API, Geyser API, or UUID prefix fallback)
 * and coordinates automatic custom item synchronization for Bedrock clients.
 * Preserves strict backward compatibility and thread safety.
 */
public class BedrockBridgeManager {

    private final WANGPlugin plugin;
    private final Set<UUID> cachedBedrockPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    
    private boolean floodgateApiAvailable = false;
    private Object floodgateApiInstance = null;
    private Method isFloodgatePlayerMethod = null;

    private boolean geyserApiAvailable = false;
    private Object geyserApiInstance = null;
    private Method isBedrockPlayerMethod = null;

    public BedrockBridgeManager(WANGPlugin plugin) {
        this.plugin = plugin;
        initReflectionHooks();
    }

    private void initReflectionHooks() {
        // Attempt to hook Floodgate API
        try {
            Class<?> floodgateClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstanceMethod = floodgateClass.getMethod("getInstance");
            floodgateApiInstance = getInstanceMethod.invoke(null);
            isFloodgatePlayerMethod = floodgateClass.getMethod("isFloodgatePlayer", UUID.class);
            floodgateApiAvailable = true;
            plugin.getLogger().info("Successfully hooked into Floodgate API for Bedrock player detection.");
        } catch (Throwable t) {
            plugin.getLogger().info("Floodgate API not found directly on server classloader. Will fall back to Geyser or UUID prefix detection.");
        }

        // Attempt to hook Geyser API
        try {
            Class<?> geyserClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = geyserClass.getMethod("api");
            geyserApiInstance = apiMethod.invoke(null);
            isBedrockPlayerMethod = geyserClass.getMethod("isBedrockPlayer", UUID.class);
            geyserApiAvailable = true;
            plugin.getLogger().info("Successfully hooked into Geyser API for Bedrock player detection.");
        } catch (Throwable t) {
            plugin.getLogger().info("Geyser API not found directly on server classloader. UUID prefix fallback enabled.");
        }
    }

    /**
     * Thread-safe check to determine if a player is connected via Bedrock Edition (Floodgate/Geyser).
     * @param uuid The unique ID of the player to check.
     * @return true if the player is identified as a Bedrock client.
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        if (cachedBedrockPlayers.contains(uuid)) {
            return true;
        }

        boolean result = false;
        if (floodgateApiAvailable && floodgateApiInstance != null && isFloodgatePlayerMethod != null) {
            try {
                Object invokeResult = isFloodgatePlayerMethod.invoke(floodgateApiInstance, uuid);
                if (invokeResult instanceof Boolean && (Boolean) invokeResult) {
                    result = true;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error checking Floodgate player status for " + uuid, e);
            }
        }

        if (!result && geyserApiAvailable && geyserApiInstance != null && isBedrockPlayerMethod != null) {
            try {
                Object invokeResult = isBedrockPlayerMethod.invoke(geyserApiInstance, uuid);
                if (invokeResult instanceof Boolean && (Boolean) invokeResult) {
                    result = true;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error checking Geyser player status for " + uuid, e);
            }
        }

        if (!result && plugin.getConfig().getBoolean("bedrock-bridge.uuid-prefix-fallback", true)) {
            String prefix = plugin.getConfig().getString("bedrock-bridge.uuid-prefix", "00000000-0000-0000-");
            if (uuid.toString().startsWith(prefix)) {
                result = true;
            }
        }

        if (result) {
            cachedBedrockPlayers.add(uuid);
        }
        return result;
    }

    public void registerPlayer(Player player) {
        if (isBedrockPlayer(player.getUniqueId())) {
            cachedBedrockPlayers.add(player.getUniqueId());
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().info("Registered Bedrock player: " + player.getName() + " (" + player.getUniqueId() + ")");
            }
        }
    }

    public void unregisterPlayer(UUID uuid) {
        cachedBedrockPlayers.remove(uuid);
    }

    public Set<UUID> getCachedBedrockPlayers() {
        return Collections.unmodifiableSet(cachedBedrockPlayers);
    }

    public void clearCache() {
        cachedBedrockPlayers.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            registerPlayer(p);
        }
    }
}
