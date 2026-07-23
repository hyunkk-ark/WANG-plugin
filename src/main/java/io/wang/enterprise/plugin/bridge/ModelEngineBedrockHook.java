package io.wang.enterprise.plugin.bridge;

import io.wang.enterprise.plugin.WANGPlugin;
import io.wang.enterprise.plugin.manager.BedrockBridgeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Enterprise hook into ModelEngine for Bedrock client synchronization.
 * Ensures custom entity models and MythicMobs have fallback visibility and display tags for Bedrock players.
 */
public class ModelEngineBedrockHook implements Listener {

    private final WANGPlugin plugin;
    private final BedrockBridgeManager bridgeManager;
    private boolean modelEngineAvailable = false;
    private Class<?> modelEngineClass = null;

    public ModelEngineBedrockHook(WANGPlugin plugin, BedrockBridgeManager bridgeManager) {
        this.plugin = plugin;
        this.bridgeManager = bridgeManager;
        initModelEngine();
    }

    private void initModelEngine() {
        try {
            modelEngineClass = Class.forName("com.ticxo.modelengine.core.ModelEngine");
            modelEngineAvailable = true;
            plugin.getLogger().info("ModelEngineBedrockHook: Connected to ModelEngine API.");
        } catch (Throwable t) {
            plugin.getLogger().info("ModelEngineBedrockHook: ModelEngine API class not found on classpath.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!modelEngineAvailable || !plugin.getConfig().getBoolean("integrations.modelengine.fallback-entity-metadata", true)) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            return;
        }

        // Run async check to see if entity is a modeled entity / MythicMob and apply Bedrock display tag if needed
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!entity.isValid()) {
                return;
            }

            try {
                if (isModelEngineEntity(entity)) {
                    applyBedrockEntityFallback((LivingEntity) entity);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Error checking ModelEngine status for entity " + entity.getUniqueId(), e);
            }
        }, 5L);
    }

    private boolean isModelEngineEntity(Entity entity) {
        try {
            if (modelEngineClass != null) {
                Method getAPI = modelEngineClass.getMethod("getAPI");
                Object apiInstance = getAPI.invoke(null);
                if (apiInstance != null) {
                    Method getModelRegistry = apiInstance.getClass().getMethod("getModelRegistry");
                    Object registry = getModelRegistry.invoke(apiInstance);
                    if (registry != null) {
                        Method getModeledEntity = registry.getClass().getMethod("getModeledEntity", java.util.UUID.class);
                        Object modeledEntity = getModeledEntity.invoke(registry, entity.getUniqueId());
                        return modeledEntity != null;
                    }
                }
            }
        } catch (Exception ignored) {
            // Safe fallback: check metadata or tags
        }
        return entity.getScoreboardTags().stream().anyMatch(tag -> tag.startsWith("modelengine:") || tag.startsWith("ME_"));
    }

    private void applyBedrockEntityFallback(LivingEntity entity) {
        boolean hasBedrockNearby = false;
        for (Player p : entity.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(entity.getLocation()) < 4096 && bridgeManager.isBedrockPlayer(p.getUniqueId())) {
                hasBedrockNearby = true;
                break;
            }
        }

        if (hasBedrockNearby && (!entity.isCustomNameVisible() || entity.getCustomName() == null)) {
            String name = ChatColor.GOLD + "[Model] " + ChatColor.WHITE + entity.getType().name();
            entity.setCustomName(name);
            entity.setCustomNameVisible(true);
        }
    }

    public boolean isModelEngineAvailable() {
        return modelEngineAvailable;
    }
}
