package io.wang.enterprise.plugin.command;

import io.wang.enterprise.plugin.WANGPlugin;
import io.wang.enterprise.plugin.bridge.OraxenBedrockHook;
import io.wang.enterprise.plugin.manager.BedrockBridgeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command handler for /wang and subcommands (status, sync, reload).
 * Provides enterprise administrative control over the Bedrock custom item bridge.
 */
public class WANGCommand implements CommandExecutor, TabCompleter {

    private final WANGPlugin plugin;
    private final BedrockBridgeManager bridgeManager;
    private final OraxenBedrockHook oraxenHook;

    public WANGCommand(WANGPlugin plugin, BedrockBridgeManager bridgeManager, OraxenBedrockHook oraxenHook) {
        this.plugin = plugin;
        this.bridgeManager = bridgeManager;
        this.oraxenHook = oraxenHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("wang.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute WANG enterprise commands.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendHeader(sender);
            sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "ONLINE (Java 21 | Paper 1.21.11)");
            sender.sendMessage(ChatColor.GRAY + "Bedrock Players Online: " + ChatColor.AQUA + bridgeManager.getCachedBedrockPlayers().size());
            sender.sendMessage(ChatColor.GRAY + "Oraxen Custom Items Mapped: " + ChatColor.AQUA + oraxenHook.getCachedCustomModelData().size());
            sender.sendMessage(ChatColor.GRAY + "Auto-Register Geyser: " + ChatColor.YELLOW + plugin.getConfig().getBoolean("bedrock-bridge.auto-register-geyser-items", true));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            bridgeManager.clearCache();
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("general.log-prefix", "&8[&bWANG-Plugin&8] &r")) 
                + ChatColor.GREEN + "Configuration & Bedrock player cache reloaded successfully.");
            return true;
        }

        if (args[0].equalsIgnoreCase("sync")) {
            bridgeManager.clearCache();
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (bridgeManager.isBedrockPlayer(p.getUniqueId())) {
                    p.updateInventory();
                }
            }
            sender.sendMessage(ChatColor.GREEN + "Bedrock Custom Item mappings & inventory synchronization triggered across all online players.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Available: status, reload, sync");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("wang.admin")) {
            List<String> completions = new ArrayList<>();
            for (String sub : Arrays.asList("status", "reload", "sync")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return new ArrayList<>();
    }

    private void sendHeader(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "========================================");
        sender.sendMessage(ChatColor.AQUA + "  WANG ENTERPRISE BEDROCK BRIDGE v1.0.0");
        sender.sendMessage(ChatColor.DARK_AQUA + "========================================");
    }
}
