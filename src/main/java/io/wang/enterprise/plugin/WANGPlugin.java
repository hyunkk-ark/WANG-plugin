package io.wang.enterprise.plugin;

import io.wang.enterprise.plugin.bridge.ModelEngineBedrockHook;
import io.wang.enterprise.plugin.bridge.OraxenBedrockHook;
import io.wang.enterprise.plugin.command.WANGCommand;
import io.wang.enterprise.plugin.listener.BedrockItemListener;
import io.wang.enterprise.plugin.manager.BedrockBridgeManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main JavaPlugin entry point for WANG Enterprise Plugin.
 * Provides automated Bedrock compatibility and custom item synchronization for Paper 1.21.11.
 * Author: AULA WANG
 */
public class WANGPlugin extends JavaPlugin {

    private BedrockBridgeManager bridgeManager;
    private OraxenBedrockHook oraxenHook;
    private ModelEngineBedrockHook modelEngineHook;
    private BedrockItemListener itemListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("==================================================");
        getLogger().info("Initializing WANG Enterprise Plugin & Bedrock Bridge");
        getLogger().info("Target Platform: Paper 1.21.11 | Java 21");
        getLogger().info("==================================================");

        // Initialize managers
        this.bridgeManager = new BedrockBridgeManager(this);
        this.oraxenHook = new OraxenBedrockHook(this, bridgeManager);
        this.modelEngineHook = new ModelEngineBedrockHook(this, bridgeManager);
        this.itemListener = new BedrockItemListener(this, bridgeManager, oraxenHook);

        // Register listeners safely
        PluginManager pm = getServer().getPluginManager();
        if (pm.getPlugin("Oraxen") != null || pm.isPluginEnabled("Oraxen")) {
            pm.registerEvents(oraxenHook, this);
            getLogger().info("Successfully hooked into Oraxen event bus.");
        } else {
            getLogger().info("Oraxen plugin not currently active. OraxenBedrockHook standing by.");
        }

        if (pm.getPlugin("ModelEngine") != null || pm.isPluginEnabled("ModelEngine")) {
            pm.registerEvents(modelEngineHook, this);
            getLogger().info("Successfully hooked into ModelEngine event bus.");
        } else {
            getLogger().info("ModelEngine plugin not currently active. ModelEngineBedrockHook standing by.");
        }

        pm.registerEvents(itemListener, this);

        // Register commands
        if (getCommand("wang") != null) {
            WANGCommand cmdExecutor = new WANGCommand(this, bridgeManager, oraxenHook);
            getCommand("wang").setExecutor(cmdExecutor);
            getCommand("wang").setTabCompleter(cmdExecutor);
        }

        // Register currently online players
        Bukkit.getOnlinePlayers().forEach(bridgeManager::registerPlayer);

        getLogger().info("WANG Enterprise Bedrock Custom Item Auto-Bridge is now active and ready!");
    }

    @Override
    public void onDisable() {
        if (bridgeManager != null) {
            bridgeManager.clearCache();
        }
        getLogger().info("WANG Enterprise Plugin cleanly disabled.");
    }

    public BedrockBridgeManager getBridgeManager() {
        return bridgeManager;
    }

    public OraxenBedrockHook getOraxenHook() {
        return oraxenHook;
    }

    public ModelEngineBedrockHook getModelEngineHook() {
        return modelEngineHook;
    }
}
