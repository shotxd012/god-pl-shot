package com.example.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.example.ShotPL;
import org.bukkit.Server;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.entity.EntityType;
import java.util.logging.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

public class ServerAPI {
    private final ShotPL plugin;
    private final Gson gson;
    private long startTime;
    private final AtomicInteger tickCount;
    private final double[] tpsHistory;
    private int tpsIndex;
    private HttpServer server;
    private long lastTickTime;
    private final Map<Statistic, EntityType> statisticEntityTypes;

    public ServerAPI(ShotPL plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.startTime = System.currentTimeMillis();
        this.lastTickTime = startTime;
        this.tickCount = new AtomicInteger(0);
        this.tpsHistory = new double[3];
        this.tpsIndex = 0;
        this.statisticEntityTypes = new HashMap<>();
        initializeStatisticEntityTypes();

        // Start TPS monitoring task
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateTPS, 20L, 20L);
    }

    private void initializeStatisticEntityTypes() {
        // Initialize entity types for statistics that require them
        statisticEntityTypes.put(Statistic.KILL_ENTITY, EntityType.PLAYER);
        statisticEntityTypes.put(Statistic.ENTITY_KILLED_BY, EntityType.PLAYER);
        statisticEntityTypes.put(Statistic.DEATHS, EntityType.PLAYER);
        // Add more mappings as needed
    }

    private void updateTPS() {
        long currentTime = System.currentTimeMillis();
        long timeSpent = currentTime - lastTickTime;
        int ticks = tickCount.getAndSet(0);

        // Calculate TPS based on actual ticks
        double tps = (ticks * 1000.0) / timeSpent;

        // Ensure TPS is within reasonable bounds (0-20)
        tps = Math.min(20.0, Math.max(0.0, tps));

        tpsHistory[tpsIndex] = tps;
        tpsIndex = (tpsIndex + 1) % tpsHistory.length;

        lastTickTime = currentTime;
    }

    public void start() {
        try {
            int port = plugin.getConfig().getInt("api.port", 8080);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            // Status endpoint
            server.createContext("/api/status", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                Map<String, Object> response = new HashMap<>();
                response.put("server_name", Bukkit.getServer().getName());
                response.put("version", Bukkit.getServer().getVersion());
                response.put("online_players", Bukkit.getOnlinePlayers().size());
                response.put("max_players", Bukkit.getMaxPlayers());
                response.put("uptime", System.currentTimeMillis() - plugin.getStartTime());
                response.put("current_tps", tpsHistory[0]);
                response.put("average_tps", (tpsHistory[0] + tpsHistory[1] + tpsHistory[2]) / 3);

                // Memory usage
                Runtime runtime = Runtime.getRuntime();
                response.put("memory_used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
                response.put("memory_max", runtime.maxMemory() / 1024 / 1024);

                // Online players
                List<Map<String, Object>> players = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Map<String, Object> playerInfo = new HashMap<>();
                    playerInfo.put("name", player.getName());
                    playerInfo.put("uuid", player.getUniqueId().toString());
                    playerInfo.put("health", player.getHealth());
                    playerInfo.put("game_mode", player.getGameMode().toString());
                    playerInfo.put("ping", player.getPing());
                    players.add(playerInfo);
                }
                response.put("players", players);

                sendResponse(exchange, 200, gson.toJson(response));
            });

            // Player info endpoint
            server.createContext("/api/player/", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String playerName = path.substring("/api/player/".length());

                // Try to find player by name (online or offline)
                Player onlinePlayer = Bukkit.getPlayer(playerName);
                UUID playerUuid = null;

                if (onlinePlayer != null) {
                    playerUuid = onlinePlayer.getUniqueId();
                } else {
                    // Try to find player UUID from database by name
                    playerUuid = plugin.getDatabaseManager().getPlayerUuidByName(playerName);
                }

                if (playerUuid == null) {
                    sendResponse(exchange, 404, "Player not found");
                    return;
                }

                Map<String, Object> response = new HashMap<>();
                response.put("name", playerName);
                response.put("uuid", playerUuid.toString());
                response.put("is_online", onlinePlayer != null);

                if (onlinePlayer != null) {
                    // Player is online - get live data
                    response.put("health", onlinePlayer.getHealth());
                    response.put("max_health", onlinePlayer.getMaxHealth());
                    response.put("game_mode", onlinePlayer.getGameMode().toString());
                    response.put("ping", onlinePlayer.getPing());
                    response.put("food_level", onlinePlayer.getFoodLevel());
                    response.put("saturation", onlinePlayer.getSaturation());
                    response.put("level", onlinePlayer.getLevel());
                    response.put("exp", onlinePlayer.getExp());
                    response.put("total_experience", onlinePlayer.getTotalExperience());
                    response.put("location", Arrays.asList(
                        onlinePlayer.getLocation().getX(),
                        onlinePlayer.getLocation().getY(),
                        onlinePlayer.getLocation().getZ()
                    ));
                    response.put("world", onlinePlayer.getWorld().getName());

                    // Add live Bukkit statistics
                    Map<String, Object> liveStats = new HashMap<>();
                    updatePlayerStats(liveStats, onlinePlayer);

                    response.put("statistics", liveStats);
                }

                // Add player stats from database
                Map<String, Object> playerStats = plugin.getDatabaseManager().getPlayerStats(playerUuid);
                response.putAll(playerStats);

                sendResponse(exchange, 200, gson.toJson(response));
            });

            // All players data endpoint
            server.createContext("/api/players", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                List<Map<String, Object>> playersData = plugin.getDatabaseManager().getAllPlayersData();

                // Enhance player data with online status and additional information
                for (Map<String, Object> playerData : playersData) {
                    String uuid = (String) playerData.get("uuid");
                    Player onlinePlayer = Bukkit.getPlayer(UUID.fromString(uuid));

                    // Add online status
                    playerData.put("is_online", onlinePlayer != null);

                    // Add additional information for online players
                    if (onlinePlayer != null) {
                        playerData.put("health", onlinePlayer.getHealth());
                        playerData.put("max_health", onlinePlayer.getMaxHealth());
                        playerData.put("game_mode", onlinePlayer.getGameMode().toString());
                        playerData.put("ping", onlinePlayer.getPing());
                        playerData.put("location", Arrays.asList(
                            onlinePlayer.getLocation().getX(),
                            onlinePlayer.getLocation().getY(),
                            onlinePlayer.getLocation().getZ()
                        ));
                        playerData.put("world", onlinePlayer.getWorld().getName());
                        playerData.put("level", onlinePlayer.getLevel());
                        playerData.put("exp", onlinePlayer.getExp());
                        playerData.put("food_level", onlinePlayer.getFoodLevel());
                        playerData.put("last_played", onlinePlayer.getLastPlayed());
                    }
                }

                sendResponse(exchange, 200, gson.toJson(playersData));
            });

            // Player stats endpoint
            server.createContext("/api/player/stats/", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String uuid = path.substring("/api/player/stats/".length());
                try {
                    Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(UUID.fromString(uuid));
                    if (stats.isEmpty()) {
                        sendResponse(exchange, 404, gson.toJson(Map.of("error", "Player not found")));
                        return;
                    }
                    
                    // Format dates
                    if (stats.containsKey("first_join")) {
                        Timestamp firstJoin = (Timestamp) stats.get("first_join");
                        stats.put("first_join", firstJoin != null ? firstJoin.toString() : null);
                    }
                    if (stats.containsKey("last_online")) {
                        Timestamp lastOnline = (Timestamp) stats.get("last_online");
                        stats.put("last_online", lastOnline != null ? lastOnline.toString() : null);
                    }

                    // Format login history dates
                    if (stats.containsKey("login_history")) {
                        List<Map<String, Object>> loginHistory = (List<Map<String, Object>>) stats.get("login_history");
                        for (Map<String, Object> login : loginHistory) {
                            if (login.containsKey("login_time")) {
                                Timestamp loginTime = (Timestamp) login.get("login_time");
                                login.put("login_time", loginTime != null ? loginTime.toString() : null);
                            }
                            if (login.containsKey("logout_time")) {
                                Timestamp logoutTime = (Timestamp) login.get("logout_time");
                                login.put("logout_time", logoutTime != null ? logoutTime.toString() : null);
                            }
                        }
                    }

                    // Calculate total playtime from sessions
                    long totalPlaytime = 0;
                    if (stats.containsKey("login_history")) {
                        List<Map<String, Object>> loginHistory = (List<Map<String, Object>>) stats.get("login_history");
                        for (Map<String, Object> login : loginHistory) {
                            if (login.containsKey("session_duration")) {
                                totalPlaytime += ((Number) login.get("session_duration")).longValue();
                            }
                        }
                    }
                    stats.put("total_playtime", totalPlaytime);

                    // Ensure all required fields are present with default values
                    Map<String, Object> statistics = (Map<String, Object>) stats.getOrDefault("statistics", new HashMap<>());
                    statistics.putIfAbsent("blocks_broken", 0);
                    statistics.putIfAbsent("blocks_placed", 0);
                    statistics.putIfAbsent("deaths", 0);
                    statistics.putIfAbsent("kills", 0);
                    statistics.putIfAbsent("mob_kills", 0);
                    statistics.putIfAbsent("jumps", 0);
                    statistics.putIfAbsent("distance_walked", 0);
                    statistics.putIfAbsent("distance_sprinted", 0);
                    statistics.putIfAbsent("distance_swum", 0);
                    statistics.putIfAbsent("distance_flown", 0);
                    statistics.putIfAbsent("total_distance", 0);
                    statistics.putIfAbsent("damage_taken", 0);
                    statistics.putIfAbsent("damage_dealt", 0);
                    statistics.putIfAbsent("fish_caught", 0);
                    statistics.putIfAbsent("animals_bred", 0);
                    statistics.putIfAbsent("items_crafted", 0);
                    statistics.putIfAbsent("items_dropped", 0);
                    statistics.putIfAbsent("food_eaten", 0);
                    statistics.putIfAbsent("time_played_ticks", 0);
                    statistics.putIfAbsent("time_played_hours", 0.0);
                    statistics.putIfAbsent("last_updated", new Timestamp(System.currentTimeMillis()).toString());
                    stats.put("statistics", statistics);

                    stats.putIfAbsent("login_history", new ArrayList<>());
                    stats.putIfAbsent("achievements", new ArrayList<>());
                    stats.putIfAbsent("is_verified", false);
                    
                    // Format the response
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("data", stats);
                    
                    // Use ObjectMapper for better JSON handling
                    String jsonResponse = new ObjectMapper().writeValueAsString(response);
                    sendResponse(exchange, 200, jsonResponse);
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, gson.toJson(Map.of("error", "Invalid UUID format")));
                } catch (Exception e) {
                    plugin.getLogger().severe("Error getting player stats: " + e.getMessage());
                    sendResponse(exchange, 500, gson.toJson(Map.of("error", "Internal server error")));
                }
            });

            // Player achievements endpoint
            server.createContext("/api/player/achievements/", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String uuid = path.substring("/api/player/achievements/".length());

                try {
                    Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(UUID.fromString(uuid));
                    if (stats.isEmpty()) {
                        sendResponse(exchange, 404, gson.toJson(Map.of("error", "Player not found")));
                        return;
                    }

                    JsonObject response = new JsonObject();
                    response.add("achievements", gson.toJsonTree(stats.get("achievements")));
                    sendResponse(exchange, 200, gson.toJson(response));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, gson.toJson(Map.of("error", "Invalid UUID format")));
                }
            });

            // Start tick counter
            Bukkit.getScheduler().runTaskTimer(plugin, () -> tickCount.incrementAndGet(), 1L, 1L);

            server.start();
            plugin.getLogger().info("» API server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("» Failed to start API server: " + e.getMessage());
        }
    }

    private boolean checkAuth(HttpExchange exchange) {
        String apiKey = plugin.getConfig().getString("api.key");
        if (apiKey == null || apiKey.isEmpty()) {
            return true;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        return authHeader != null && authHeader.equals("Bearer " + apiKey);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        try {
            // Set headers
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            
            // Compress the response
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
                gzipOut.write(responseBytes);
            }
            byte[] compressedBytes = baos.toByteArray();
            
            // Send response with compressed data
            exchange.sendResponseHeaders(statusCode, compressedBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(compressedBytes);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error sending response: " + e.getMessage());
            // If compression fails, try sending uncompressed
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to send uncompressed response: " + ex.getMessage());
                throw ex;
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("§cAPI server stopped");
        }
    }

    private int getSafeStatistic(Player player, org.bukkit.Statistic statistic) {
        try {
            // Handle statistics that require Material parameter
            if (statistic == org.bukkit.Statistic.MINE_BLOCK || 
                statistic == org.bukkit.Statistic.USE_ITEM || 
                statistic == org.bukkit.Statistic.CRAFT_ITEM ||
                statistic == org.bukkit.Statistic.BREAK_ITEM ||
                statistic == org.bukkit.Statistic.PICKUP ||
                statistic == org.bukkit.Statistic.DROP) {
                int total = 0;
                for (org.bukkit.Material material : org.bukkit.Material.values()) {
                    try {
                        // Check if the material is valid for this statistic
                        if ((statistic == org.bukkit.Statistic.MINE_BLOCK && material.isBlock()) ||
                            (statistic == org.bukkit.Statistic.USE_ITEM && material.isItem()) ||
                            (statistic == org.bukkit.Statistic.CRAFT_ITEM && material.isItem()) ||
                            (statistic == org.bukkit.Statistic.BREAK_ITEM && material.isItem() && material.getMaxDurability() > 0) ||
                            (statistic == org.bukkit.Statistic.PICKUP && material.isItem()) ||
                            (statistic == org.bukkit.Statistic.DROP && material.isItem())) {
                            total += player.getStatistic(statistic, material);
                        }
                    } catch (Exception ignored) {
                        // Skip invalid materials for this statistic
                    }
                }
                return total;
            }
            
            // Handle statistics that require EntityType parameter
            if (statistic == org.bukkit.Statistic.KILL_ENTITY ||
                statistic == org.bukkit.Statistic.ENTITY_KILLED_BY) {
                int total = 0;
                for (org.bukkit.entity.EntityType entityType : org.bukkit.entity.EntityType.values()) {
                    if (entityType.isAlive()) {
                        try {
                            total += player.getStatistic(statistic, entityType);
                        } catch (Exception ignored) {
                            // Skip invalid entity types
                        }
                    }
                }
                return total;
            }

            // For simple statistics that don't require additional parameters
            return player.getStatistic(statistic);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get statistic " + statistic.name() + " for player " + player.getName(), e);
            return 0;
        }
    }

    private void updatePlayerStats(Map<String, Object> liveStats, Player player) {
        try {
            // Block statistics
            liveStats.put("blocks_broken", getSafeStatistic(player, org.bukkit.Statistic.MINE_BLOCK));
            liveStats.put("blocks_placed", getSafeStatistic(player, org.bukkit.Statistic.USE_ITEM));
            
            // Combat statistics
            liveStats.put("deaths", getSafeStatistic(player, org.bukkit.Statistic.DEATHS));
            liveStats.put("player_kills", getSafeStatistic(player, org.bukkit.Statistic.PLAYER_KILLS));
            liveStats.put("mob_kills", getSafeStatistic(player, org.bukkit.Statistic.MOB_KILLS));
            liveStats.put("jumps", getSafeStatistic(player, org.bukkit.Statistic.JUMP));
            
            // Movement statistics
            int walkDistance = getSafeStatistic(player, org.bukkit.Statistic.WALK_ONE_CM);
            int sprintDistance = getSafeStatistic(player, org.bukkit.Statistic.SPRINT_ONE_CM);
            int swimDistance = getSafeStatistic(player, org.bukkit.Statistic.SWIM_ONE_CM);
            int flyDistance = getSafeStatistic(player, org.bukkit.Statistic.FLY_ONE_CM);
            
            liveStats.put("distance_walked", walkDistance);
            liveStats.put("distance_sprinted", sprintDistance);
            liveStats.put("distance_swum", swimDistance);
            liveStats.put("distance_flown", flyDistance);
            liveStats.put("total_distance", walkDistance + sprintDistance + swimDistance + flyDistance);
            
            // Other statistics
            liveStats.put("damage_taken", getSafeStatistic(player, org.bukkit.Statistic.DAMAGE_TAKEN));
            liveStats.put("damage_dealt", getSafeStatistic(player, org.bukkit.Statistic.DAMAGE_DEALT));
            liveStats.put("fish_caught", getSafeStatistic(player, org.bukkit.Statistic.FISH_CAUGHT));
            liveStats.put("animals_bred", getSafeStatistic(player, org.bukkit.Statistic.ANIMALS_BRED));
            liveStats.put("items_crafted", getSafeStatistic(player, org.bukkit.Statistic.CRAFT_ITEM));
            liveStats.put("items_dropped", getSafeStatistic(player, org.bukkit.Statistic.DROP));
            liveStats.put("food_eaten", getSafeStatistic(player, org.bukkit.Statistic.USE_ITEM));
            
            // Time played statistics
            int timePlayedTicks = getSafeStatistic(player, org.bukkit.Statistic.PLAY_ONE_MINUTE);
            liveStats.put("time_played_ticks", timePlayedTicks);
            liveStats.put("time_played_hours", timePlayedTicks / 72000.0);

            // Add last updated timestamp
            liveStats.put("last_updated", new Timestamp(System.currentTimeMillis()).toString());

            // Save the current statistics to database
            plugin.getDatabaseManager().updatePlayerStats(player.getUniqueId(), liveStats);

            // Add verification status
            try {
                boolean isVerified = plugin.getDatabaseManager().isPlayerVerified(player.getUniqueId());
                liveStats.put("is_verified", isVerified);
                if (!isVerified) {
                    String verificationCode = plugin.getDatabaseManager().getVerificationCode(player.getUniqueId());
                    liveStats.put("verification_code", verificationCode);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error getting verification status for player " + player.getName() + ": " + e.getMessage());
                liveStats.put("is_verified", false);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting live statistics for player " + player.getName() + ": " + e.getMessage());
        }
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - startTime;
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        return String.format("%dd %dh %dm %ds", 
            days, hours % 24, minutes % 60, seconds % 60);
    }

    private void setupRoutes() {
        // Player stats endpoint
        server.createContext("/api/player/stats/", exchange -> {
            if (!checkAuth(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String uuid = path.substring("/api/player/stats/".length());
            try {
                Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(UUID.fromString(uuid));
                if (stats.isEmpty()) {
                    sendResponse(exchange, 404, gson.toJson(Map.of("error", "Player not found")));
                    return;
                }
                
                // Format dates
                if (stats.containsKey("first_join")) {
                    Timestamp firstJoin = (Timestamp) stats.get("first_join");
                    stats.put("first_join", firstJoin != null ? firstJoin.toString() : null);
                }
                if (stats.containsKey("last_online")) {
                    Timestamp lastOnline = (Timestamp) stats.get("last_online");
                    stats.put("last_online", lastOnline != null ? lastOnline.toString() : null);
                }

                // Format login history dates
                if (stats.containsKey("login_history")) {
                    List<Map<String, Object>> loginHistory = (List<Map<String, Object>>) stats.get("login_history");
                    for (Map<String, Object> login : loginHistory) {
                        if (login.containsKey("login_time")) {
                            Timestamp loginTime = (Timestamp) login.get("login_time");
                            login.put("login_time", loginTime != null ? loginTime.toString() : null);
                        }
                        if (login.containsKey("logout_time")) {
                            Timestamp logoutTime = (Timestamp) login.get("logout_time");
                            login.put("logout_time", logoutTime != null ? logoutTime.toString() : null);
                        }
                    }
                }

                // Calculate total playtime from sessions
                long totalPlaytime = 0;
                if (stats.containsKey("login_history")) {
                    List<Map<String, Object>> loginHistory = (List<Map<String, Object>>) stats.get("login_history");
                    for (Map<String, Object> login : loginHistory) {
                        if (login.containsKey("session_duration")) {
                            totalPlaytime += ((Number) login.get("session_duration")).longValue();
                        }
                    }
                }
                stats.put("total_playtime", totalPlaytime);

                // Ensure all required fields are present with default values
                Map<String, Object> statistics = (Map<String, Object>) stats.getOrDefault("statistics", new HashMap<>());
                statistics.putIfAbsent("blocks_broken", 0);
                statistics.putIfAbsent("blocks_placed", 0);
                statistics.putIfAbsent("deaths", 0);
                statistics.putIfAbsent("kills", 0);
                statistics.putIfAbsent("mob_kills", 0);
                statistics.putIfAbsent("jumps", 0);
                statistics.putIfAbsent("distance_walked", 0);
                statistics.putIfAbsent("distance_sprinted", 0);
                statistics.putIfAbsent("distance_swum", 0);
                statistics.putIfAbsent("distance_flown", 0);
                statistics.putIfAbsent("total_distance", 0);
                statistics.putIfAbsent("damage_taken", 0);
                statistics.putIfAbsent("damage_dealt", 0);
                statistics.putIfAbsent("fish_caught", 0);
                statistics.putIfAbsent("animals_bred", 0);
                statistics.putIfAbsent("items_crafted", 0);
                statistics.putIfAbsent("items_dropped", 0);
                statistics.putIfAbsent("food_eaten", 0);
                statistics.putIfAbsent("time_played_ticks", 0);
                statistics.putIfAbsent("time_played_hours", 0.0);
                statistics.putIfAbsent("last_updated", new Timestamp(System.currentTimeMillis()).toString());
                stats.put("statistics", statistics);

                stats.putIfAbsent("login_history", new ArrayList<>());
                stats.putIfAbsent("achievements", new ArrayList<>());
                stats.putIfAbsent("is_verified", false);
                
                // Format the response
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", stats);
                
                // Use ObjectMapper for better JSON handling
                String jsonResponse = new ObjectMapper().writeValueAsString(response);
                sendResponse(exchange, 200, jsonResponse);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, gson.toJson(Map.of("error", "Invalid UUID format")));
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting player stats: " + e.getMessage());
                sendResponse(exchange, 500, gson.toJson(Map.of("error", "Internal server error")));
            }
        });

        // All players endpoint
        server.createContext("/api/players", exchange -> {
            if (!checkAuth(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                List<Map<String, Object>> players = plugin.getDatabaseManager().getAllPlayersData();
                
                // Format the response
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("data", players);
                
                // Use ObjectMapper for better JSON handling
                String jsonResponse = new ObjectMapper().writeValueAsString(response);
                sendResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting all players: " + e.getMessage());
                sendResponse(exchange, 500, gson.toJson(Map.of("error", "Internal server error")));
            }
        });

        // Player verification endpoint
        server.createContext("/api/player/verify", exchange -> {
            if (!checkAuth(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> requestBody = gson.fromJson(body, Map.class);
                String uuid = requestBody.get("uuid");
                String code = requestBody.get("code");

                if (uuid == null || code == null) {
                    sendResponse(exchange, 400, gson.toJson(Map.of("error", "Missing required fields")));
                    return;
                }

                boolean isValid = plugin.getDatabaseManager().isPlayerVerified(UUID.fromString(uuid));
                if (isValid) {
                    sendResponse(exchange, 200, gson.toJson(Map.of(
                        "success", true,
                        "message", "Player is already verified"
                    )));
                    return;
                }

                String storedCode = plugin.getDatabaseManager().getVerificationCode(UUID.fromString(uuid));
                if (storedCode != null && storedCode.equals(code)) {
                    plugin.getDatabaseManager().setPlayerVerified(UUID.fromString(uuid), true);
                    sendResponse(exchange, 200, gson.toJson(Map.of(
                        "success", true,
                        "message", "Verification successful"
                    )));
                } else {
                    sendResponse(exchange, 400, gson.toJson(Map.of(
                        "success", false,
                        "message", "Invalid verification code"
                    )));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error verifying player: " + e.getMessage());
                sendResponse(exchange, 500, gson.toJson(Map.of("error", "Internal server error")));
            }
        });
    }
}