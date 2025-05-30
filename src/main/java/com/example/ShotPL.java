package com.example;

// Removed stats imports
import com.example.api.ServerAPI;
import com.example.commands.LeaderboardCommand;
import com.example.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.Duration;

public class ShotPL extends JavaPlugin implements Listener {
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

        // Initialize API server
        api = new ServerAPI(this);
        api.start();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));

        // Log startup message
        getLogger().info("§a§lShot-PL §7» §fPlugin has been enabled!");
        getLogger().info("§a§lShot-PL §7» §fVersion: " + getDescription().getVersion());
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
        getLogger().info("§c§lShot-PL §7» §fPlugin has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Record player login
        databaseManager.recordPlayerLogin(player);

        // Update player status on join
        Bukkit.getScheduler().runTaskLater(this, () -> {
            databaseManager.updatePlayerStatus(player);
        }, 20L); // Delay by 1 second to ensure player is fully loaded

        // Send welcome message if enabled
        if (getConfig().getBoolean("welcome.enabled", true)) {
            String message = getConfig().getString("welcome.message", "§b§lShot-PL §7» §fWelcome {player} to the server!")
                    .replace("{player}", player.getName())
                    .replace("{server}", Bukkit.getServer().getName());
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        databaseManager.recordPlayerLogout(player);
        // Statistics are automatically updated in recordPlayerLogout method
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