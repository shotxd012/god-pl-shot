package com.example.commands;

import com.example.ShotPL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Statistic;

import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final ShotPL plugin;
    private final Map<String, Comparator<Player>> sorters;

    public LeaderboardCommand(ShotPL plugin) {
        this.plugin = plugin;
        this.sorters = new HashMap<>();
        initializeSorters();
    }

    private void initializeSorters() {
        sorters.put("kills", Comparator.comparingInt(p -> p.getStatistic(Statistic.PLAYER_KILLS)));
        sorters.put("deaths", Comparator.comparingInt(p -> p.getStatistic(Statistic.DEATHS)));
        sorters.put("playtime", Comparator.comparingLong(p -> 
            (Long) plugin.getDatabaseManager().getPlayerStats(p.getUniqueId()).getOrDefault("total_playtime", 0L)));
        sorters.put("blocks", Comparator.comparingInt(p -> 
            p.getStatistic(Statistic.BREAK_ITEM) + p.getStatistic(Statistic.USE_ITEM)));
        sorters.put("distance", Comparator.comparingInt(p -> 
            p.getStatistic(Statistic.WALK_ONE_CM) + 
            p.getStatistic(Statistic.SPRINT_ONE_CM) + 
            p.getStatistic(Statistic.SWIM_ONE_CM)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shotpl.leaderboard")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        String category = args.length > 0 ? args[0].toLowerCase() : "kills";
        if (!sorters.containsKey(category)) {
            sender.sendMessage(ChatColor.RED + "Invalid category! Available categories: " + 
                String.join(", ", sorters.keySet()));
            return true;
        }

        showLeaderboard(sender, category);
        return true;
    }

    private void showLeaderboard(CommandSender sender, String category) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(sorters.get(category).reversed());

        sender.sendMessage(ChatColor.GOLD + "§8§m----------------------------------------");
        sender.sendMessage(ChatColor.YELLOW + "§lTop Players - " + ChatColor.WHITE + 
            category.substring(0, 1).toUpperCase() + category.substring(1));
        sender.sendMessage(ChatColor.GOLD + "§8§m----------------------------------------");

        for (int i = 0; i < Math.min(10, players.size()); i++) {
            Player player = players.get(i);
            String value = getValue(player, category);
            sender.sendMessage(ChatColor.GOLD + "#" + (i + 1) + " " + 
                ChatColor.WHITE + player.getName() + ChatColor.GRAY + " - " + 
                ChatColor.YELLOW + value);
        }

        sender.sendMessage(ChatColor.GOLD + "§8§m----------------------------------------");
    }

    private String getValue(Player player, String category) {
        switch (category) {
            case "kills":
                return String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS));
            case "deaths":
                return String.valueOf(player.getStatistic(Statistic.DEATHS));
            case "playtime":
                long playtime = (Long) plugin.getDatabaseManager().getPlayerStats(player.getUniqueId()).getOrDefault("total_playtime", 0L);
                return formatDuration(playtime);
            case "jumps":
                return String.valueOf(player.getStatistic(Statistic.JUMP));
            case "distance":
                return String.valueOf((player.getStatistic(Statistic.WALK_ONE_CM) + 
                    player.getStatistic(Statistic.SPRINT_ONE_CM) + 
                    player.getStatistic(Statistic.SWIM_ONE_CM)) / 100) + " blocks";
            default:
                return "0";
        }
    }

    private String formatDuration(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600) % 24;
        long days = totalSeconds / 86400;

        return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return sorters.keySet().stream()
                .filter(category -> category.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}