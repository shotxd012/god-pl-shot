package com.example;

// Removed stats imports
import com.example.api.ServerAPI;
import com.example.commands.VerifyCommand;
import com.example.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;

public class ShotPL extends JavaPlugin {
    private ServerAPI api;
    private DatabaseManager databaseManager;
    private long startTime;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize start time
        startTime = System.currentTimeMillis();

        // Initialize database
        databaseManager = new DatabaseManager(this);

        // Register commands
        getCommand("verify").setExecutor(new VerifyCommand(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Start API server if enabled
        if (getConfig().getBoolean("api.enabled", false)) {
            api = new ServerAPI(this);
            api.start();
        }

        // Log startup message
        getLogger().info("Shot-PL has been enabled!");
        getLogger().info("Version: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // Stop API server if running
        if (api != null) {
            api.stop();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Log shutdown message
        getLogger().info("Plugin has been disabled!");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public String formatPlaytime(long playtimeMillis) {
        Duration duration = Duration.ofMillis(playtimeMillis);
        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);
        long seconds = duration.getSeconds();

        return String.format("%dd, %dh, %dm, %ds", days, hours, minutes, seconds);
    }

    public long getStartTime() {
        return startTime;
    }
}