package com.example;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    private final ShotPL plugin;

    public PlayerListener(ShotPL plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Record player login
        plugin.getDatabaseManager().recordPlayerLogin(player);

        // Update player status on join
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getDatabaseManager().updatePlayerStatus(player);
        }, 20L); // Delay by 1 second to ensure player is fully loaded

        // Send welcome message if enabled
        if (plugin.getConfig().getBoolean("welcome.enabled", true)) {
            String message = plugin.getConfig().getString("welcome.message", "§b§lShot-PL §7» §fWelcome {player} to the server!")
                    .replace("{player}", player.getName())
                    .replace("{server}", plugin.getServer().getName());
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getDatabaseManager().recordPlayerLogout(player);
    }
} 