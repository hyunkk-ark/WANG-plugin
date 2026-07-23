package io.wang.enterprise.plugin.listener;

import io.wang.enterprise.plugin.WANGPlugin;
import io.wang.enterprise.plugin.bridge.OraxenBedrockHook;
import io.wang.enterprise.plugin.manager.BedrockBridgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for inventory and player lifecycle events to synchronize custom item display
 * specifically for Bedrock Edition players without requiring extra Bedrock client setups.
 */
public class BedrockItemListener implements Listener {

    private final WANGPlugin plugin;
    private final BedrockBridgeManager bridgeManager;
    private final OraxenBedrockHook oraxenHook;

    public BedrockItemListener(WANGPlugin plugin, BedrockBridgeManager bridgeManager, OraxenBedrockHook oraxenHook) {
        this.plugin = plugin;
        this.bridgeManager = bridgeManager;
        this.oraxenHook = oraxenHook;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bridgeManager.registerPlayer(player);

        if (bridgeManager.isBedrockPlayer(player.getUniqueId()) && plugin.getConfig().getBoolean("bedrock-bridge.auto-format-bedrock-inventory", true)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    formatPlayerInventory(player);
                }
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        bridgeManager.unregisterPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        if (!bridgeManager.isBedrockPlayer(player.getUniqueId()) || !plugin.getConfig().getBoolean("bedrock-bridge.auto-format-bedrock-inventory", true)) {
            return;
        }

        Inventory inv = event.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) {
                ItemStack formatted = oraxenHook.formatBedrockItem(item);
                if (formatted != item && !formatted.equals(item)) {
                    inv.setItem(i, formatted);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (!bridgeManager.isBedrockPlayer(player.getUniqueId()) || !plugin.getConfig().getBoolean("bedrock-bridge.auto-format-bedrock-inventory", true)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null) {
            ItemStack formatted = oraxenHook.formatBedrockItem(current);
            if (formatted != current && !formatted.equals(current)) {
                event.setCurrentItem(formatted);
            }
        }
    }

    private void formatPlayerInventory(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null) {
                ItemStack formatted = oraxenHook.formatBedrockItem(item);
                if (formatted != item && !formatted.equals(item)) {
                    inv.setItem(i, formatted);
                }
            }
        }
        player.updateInventory();
    }
}
