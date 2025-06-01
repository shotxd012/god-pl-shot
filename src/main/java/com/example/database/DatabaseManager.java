package com.example.database;

import com.example.ShotPL;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final ShotPL plugin;
    private Connection connection;

    public DatabaseManager(ShotPL plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/player_data.db");
            
            // Create tables if they don't exist
            try (Statement stmt = connection.createStatement()) {
                // Players table
                stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_online TIMESTAMP," +
                    "total_playtime INTEGER DEFAULT 0" +
                    ")");

                // Verification table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_verification (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "verification_code TEXT," +
                    "is_verified BOOLEAN DEFAULT FALSE," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "verified_at TIMESTAMP" +
                    ")");

                // Player sessions table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "player_name TEXT NOT NULL," +
                    "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "logout_time TIMESTAMP," +
                    "session_duration INTEGER DEFAULT 0" +
                    ")");

                // Player achievements table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_achievements (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "achievement_id TEXT NOT NULL," +
                    "progress INTEGER DEFAULT 0," +
                    "completed BOOLEAN DEFAULT FALSE," +
                    "completed_at TIMESTAMP," +
                    "UNIQUE(player_uuid, achievement_id)" +
                    ")");

                // Player statistics table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_statistics (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "blocks_broken INTEGER DEFAULT 0," +
                    "blocks_placed INTEGER DEFAULT 0," +
                    "deaths INTEGER DEFAULT 0," +
                    "player_kills INTEGER DEFAULT 0," +
                    "mob_kills INTEGER DEFAULT 0," +
                    "jumps INTEGER DEFAULT 0," +
                    "distance_walked INTEGER DEFAULT 0," +
                    "distance_sprinted INTEGER DEFAULT 0," +
                    "distance_swum INTEGER DEFAULT 0," +
                    "distance_flown INTEGER DEFAULT 0," +
                    "total_distance INTEGER DEFAULT 0," +
                    "damage_taken INTEGER DEFAULT 0," +
                    "damage_dealt INTEGER DEFAULT 0," +
                    "fish_caught INTEGER DEFAULT 0," +
                    "animals_bred INTEGER DEFAULT 0," +
                    "items_crafted INTEGER DEFAULT 0," +
                    "items_dropped INTEGER DEFAULT 0," +
                    "food_eaten INTEGER DEFAULT 0," +
                    "time_played_ticks INTEGER DEFAULT 0," +
                    "time_played_hours REAL DEFAULT 0.0," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

                // Player status table for storing last known player state
                stmt.execute("CREATE TABLE IF NOT EXISTS player_status (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "player_name TEXT NOT NULL," +
                    "health REAL DEFAULT 20," +
                    "max_health REAL DEFAULT 20," +
                    "food_level INTEGER DEFAULT 20," +
                    "saturation REAL DEFAULT 5," +
                    "game_mode TEXT DEFAULT 'SURVIVAL'," +
                    "level INTEGER DEFAULT 0," +
                    "exp REAL DEFAULT 0," +
                    "total_experience INTEGER DEFAULT 0," +
                    "location_x REAL DEFAULT 0," +
                    "location_y REAL DEFAULT 64," +
                    "location_z REAL DEFAULT 0," +
                    "world_name TEXT DEFAULT 'world'," +
                    "ping INTEGER DEFAULT 0," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    public void recordPlayerLogin(Player player) {
        String sql = "INSERT INTO player_sessions (player_uuid, player_name) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to record player login: " + e.getMessage());
        }
    }

    public void recordPlayerLogout(Player player) {
        String sql = "UPDATE player_sessions SET logout_time = CURRENT_TIMESTAMP, " +
                    "session_duration = (strftime('%s', CURRENT_TIMESTAMP) - strftime('%s', login_time)) * 1000 " +
                    "WHERE player_uuid = ? AND logout_time IS NULL " +
                    "AND id = (SELECT id FROM player_sessions WHERE player_uuid = ? AND logout_time IS NULL ORDER BY login_time DESC LIMIT 1)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getUniqueId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to record player logout: " + e.getMessage());
        }
        
        // Update player statistics and status when they logout
        updatePlayerStatistics(player);
        updatePlayerStatus(player);
    }

    public void updatePlayerStatus(Player player) {
        try (Connection conn = getConnection()) {
            if (conn == null) return;

            String sql = "INSERT OR REPLACE INTO player_status (" +
                        "player_uuid, player_name, health, max_health, food_level, saturation, " +
                        "game_mode, level, exp, total_experience, location_x, location_y, location_z, " +
                        "world_name, ping, last_updated) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, player.getUniqueId().toString());
                pstmt.setString(2, player.getName());
                pstmt.setDouble(3, player.getHealth());
                pstmt.setDouble(4, player.getMaxHealth());
                pstmt.setInt(5, player.getFoodLevel());
                pstmt.setDouble(6, player.getSaturation());
                pstmt.setString(7, player.getGameMode().toString());
                pstmt.setInt(8, player.getLevel());
                pstmt.setDouble(9, player.getExp());
                pstmt.setInt(10, player.getTotalExperience());
                pstmt.setDouble(11, player.getLocation().getX());
                pstmt.setDouble(12, player.getLocation().getY());
                pstmt.setDouble(13, player.getLocation().getZ());
                pstmt.setString(14, player.getWorld().getName());
                pstmt.setInt(15, player.getPing());
                pstmt.executeUpdate();
            }

            // Update last online time in players table
            String lastOnlineSql = "INSERT OR REPLACE INTO players (uuid, name, last_online) VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement pstmt = conn.prepareStatement(lastOnlineSql)) {
                pstmt.setString(1, player.getUniqueId().toString());
                pstmt.setString(2, player.getName());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update player status: " + e.getMessage());
        }
    }

    public void updatePlayerStatistics(Player player) {
        String sql = "INSERT OR REPLACE INTO player_statistics (" +
                    "player_uuid, blocks_broken, blocks_placed, deaths, kills, " +
                    "distance_walked, distance_sprinted, distance_swum, distance_flown, " +
                    "jumps, food_eaten, damage_taken, damage_dealt, fish_caught, " +
                    "items_crafted, mobs_killed, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            
            // Block statistics
            pstmt.setInt(2, getSafeStat(player, org.bukkit.Statistic.MINE_BLOCK));
            pstmt.setInt(3, getSafeStatTotal(player, org.bukkit.Statistic.USE_ITEM));
            
            // Combat statistics
            pstmt.setInt(4, getSafeStat(player, org.bukkit.Statistic.DEATHS));
            pstmt.setInt(5, getSafeStat(player, org.bukkit.Statistic.PLAYER_KILLS));
            
            // Movement statistics
            pstmt.setInt(6, getSafeStat(player, org.bukkit.Statistic.WALK_ONE_CM));
            pstmt.setInt(7, getSafeStat(player, org.bukkit.Statistic.SPRINT_ONE_CM));
            pstmt.setInt(8, getSafeStat(player, org.bukkit.Statistic.SWIM_ONE_CM));
            pstmt.setInt(9, getSafeStat(player, org.bukkit.Statistic.FLY_ONE_CM));
            pstmt.setInt(10, getSafeStat(player, org.bukkit.Statistic.JUMP));
            
            // Activity statistics
            pstmt.setInt(11, getSafeStat(player, org.bukkit.Statistic.ANIMALS_BRED));
            pstmt.setInt(12, getSafeStat(player, org.bukkit.Statistic.DAMAGE_TAKEN));
            pstmt.setInt(13, getSafeStat(player, org.bukkit.Statistic.DAMAGE_DEALT));
            pstmt.setInt(14, getSafeStat(player, org.bukkit.Statistic.FISH_CAUGHT));
            pstmt.setInt(15, getSafeStatTotal(player, org.bukkit.Statistic.CRAFT_ITEM));
            pstmt.setInt(16, getSafeStat(player, org.bukkit.Statistic.MOB_KILLS));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update player statistics: " + e.getMessage());
        }
    }

    private int getSafeStat(Player player, org.bukkit.Statistic statistic) {
        try {
            if (statistic == org.bukkit.Statistic.MINE_BLOCK) {
                int total = 0;
                for (org.bukkit.Material material : org.bukkit.Material.values()) {
                    if (material.isBlock()) {
                        try {
                            total += player.getStatistic(statistic, material);
                        } catch (Exception ignored) {
                            // Some materials might not be valid for this statistic
                        }
                    }
                }
                return total;
            }
            return player.getStatistic(statistic);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get statistic " + statistic.name() + " for player " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private int getSafeStat(Player player, org.bukkit.Statistic statistic, org.bukkit.Material material) {
        try {
            return player.getStatistic(statistic, material);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get statistic " + statistic.name() + " with material " + material.name() + " for player " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private int getSafeStatTotal(Player player, org.bukkit.Statistic statistic) {
        try {
            // For statistics that require materials/entities, we need to sum across all types
            if (statistic == org.bukkit.Statistic.USE_ITEM || statistic == org.bukkit.Statistic.CRAFT_ITEM) {
                int total = 0;
                for (org.bukkit.Material material : org.bukkit.Material.values()) {
                    if (material.isItem()) {
                        try {
                            total += player.getStatistic(statistic, material);
                        } catch (Exception ignored) {
                            // Some materials might not be valid for this statistic
                        }
                    }
                }
                return total;
            } else {
                return player.getStatistic(statistic);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get total statistic " + statistic.name() + " for player " + player.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/player_data.db");
                // Enable foreign keys
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                }
            }
            return connection;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get database connection: " + e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getPlayerStats(UUID playerUuid) {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = getConnection()) {
            if (conn == null) return stats;

            // Get player statistics
            String sql = "SELECT * FROM player_statistics WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    Map<String, Object> statistics = new HashMap<>();
                    statistics.put("blocks_broken", rs.getInt("blocks_broken"));
                    statistics.put("blocks_placed", rs.getInt("blocks_placed"));
                    statistics.put("deaths", rs.getInt("deaths"));
                    statistics.put("player_kills", rs.getInt("player_kills"));
                    statistics.put("mob_kills", rs.getInt("mob_kills"));
                    statistics.put("jumps", rs.getInt("jumps"));
                    statistics.put("distance_walked", rs.getInt("distance_walked"));
                    statistics.put("distance_sprinted", rs.getInt("distance_sprinted"));
                    statistics.put("distance_swum", rs.getInt("distance_swum"));
                    statistics.put("distance_flown", rs.getInt("distance_flown"));
                    statistics.put("total_distance", rs.getInt("total_distance"));
                    statistics.put("damage_taken", rs.getInt("damage_taken"));
                    statistics.put("damage_dealt", rs.getInt("damage_dealt"));
                    statistics.put("fish_caught", rs.getInt("fish_caught"));
                    statistics.put("animals_bred", rs.getInt("animals_bred"));
                    statistics.put("items_crafted", rs.getInt("items_crafted"));
                    statistics.put("items_dropped", rs.getInt("items_dropped"));
                    statistics.put("food_eaten", rs.getInt("food_eaten"));
                    statistics.put("time_played_ticks", rs.getInt("time_played_ticks"));
                    statistics.put("time_played_hours", rs.getDouble("time_played_hours"));
                    statistics.put("last_updated", rs.getTimestamp("last_updated"));
                    stats.put("statistics", statistics);
                }
            }

            // Get player login history
            String loginHistorySql = "SELECT * FROM player_sessions WHERE player_uuid = ? ORDER BY login_time DESC LIMIT 3";
            try (PreparedStatement stmt = conn.prepareStatement(loginHistorySql)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                List<Map<String, Object>> loginHistory = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> login = new HashMap<>();
                    login.put("login_time", rs.getTimestamp("login_time"));
                    login.put("logout_time", rs.getTimestamp("logout_time"));
                    login.put("session_duration", rs.getLong("session_duration"));
                    loginHistory.add(login);
                }
                stats.put("login_history", loginHistory);
            }

            // Get player achievements
            String achievementsSql = "SELECT achievement_id FROM player_achievements WHERE player_uuid = ? AND completed = 1";
            try (PreparedStatement stmt = conn.prepareStatement(achievementsSql)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                List<String> achievements = new ArrayList<>();
                while (rs.next()) {
                    achievements.add(rs.getString("achievement_id"));
                }
                stats.put("achievements", achievements);
            }

            // Get player basic info
            String playerSql = "SELECT * FROM players WHERE uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(playerSql)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    stats.put("first_join", rs.getTimestamp("first_join"));
                    stats.put("last_online", rs.getTimestamp("last_online"));
                    stats.put("total_playtime", rs.getLong("total_playtime"));
                }
            }

            // Get verification status
            String verificationSql = "SELECT is_verified, verification_code FROM player_verification WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(verificationSql)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    boolean isVerified = rs.getBoolean("is_verified");
                    stats.put("is_verified", isVerified);
                    if (!isVerified) {
                        stats.put("verification_code", rs.getString("verification_code"));
                    }
                } else {
                    stats.put("is_verified", false);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting player stats: " + e.getMessage());
        }
        return stats;
    }

    public List<Map<String, Object>> getAllPlayersData() {
        List<Map<String, Object>> playersData = new ArrayList<>();
        try (Connection conn = getConnection()) {
            if (conn == null) return playersData;

            String sql = "SELECT DISTINCT p.uuid, p.name, p.first_join, p.last_online, p.total_playtime, " +
                        "(SELECT COUNT(*) FROM player_achievements WHERE player_uuid = p.uuid AND completed = 1) as completed_achievements " +
                        "FROM players p " +
                        "ORDER BY p.total_playtime DESC";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    Map<String, Object> playerData = new HashMap<>();
                    String uuid = rs.getString("uuid");
                    playerData.put("uuid", uuid);
                    playerData.put("name", rs.getString("name"));
                    playerData.put("total_playtime", rs.getLong("total_playtime"));
                    playerData.put("first_join", rs.getString("first_join"));
                    playerData.put("last_online", rs.getString("last_online"));
                    playerData.put("completed_achievements", rs.getInt("completed_achievements"));
                    
                    // Get recent sessions
                    String sessionsSql = "SELECT login_time, logout_time, session_duration " +
                                       "FROM player_sessions " +
                                       "WHERE player_uuid = ? " +
                                       "ORDER BY login_time DESC LIMIT 3";
                    try (PreparedStatement pstmt = conn.prepareStatement(sessionsSql)) {
                        pstmt.setString(1, uuid);
                        ResultSet sessionsRs = pstmt.executeQuery();
                        List<Map<String, Object>> sessions = new ArrayList<>();
                        while (sessionsRs.next()) {
                            Map<String, Object> session = new HashMap<>();
                            session.put("login_time", sessionsRs.getString("login_time"));
                            session.put("logout_time", sessionsRs.getString("logout_time"));
                            session.put("session_duration", sessionsRs.getLong("session_duration"));
                            sessions.add(session);
                        }
                        playerData.put("recent_sessions", sessions);
                    }

                    // Check if player is currently online
                    org.bukkit.entity.Player onlinePlayer = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(uuid));
                    playerData.put("is_online", onlinePlayer != null);
                    
                    if (onlinePlayer != null) {
                        // Player is online - get live data
                        playerData.put("health", onlinePlayer.getHealth());
                        playerData.put("max_health", onlinePlayer.getMaxHealth());
                        playerData.put("food_level", onlinePlayer.getFoodLevel());
                        playerData.put("saturation", onlinePlayer.getSaturation());
                        playerData.put("game_mode", onlinePlayer.getGameMode().toString());
                        playerData.put("level", onlinePlayer.getLevel());
                        playerData.put("exp", onlinePlayer.getExp());
                        playerData.put("total_experience", onlinePlayer.getTotalExperience());
                        playerData.put("location", Arrays.asList(
                            onlinePlayer.getLocation().getX(),
                            onlinePlayer.getLocation().getY(),
                            onlinePlayer.getLocation().getZ()
                        ));
                        playerData.put("world", onlinePlayer.getWorld().getName());
                        playerData.put("ping", onlinePlayer.getPing());
                    } else {
                        // Player is offline - get last known status from database
                        String statusSql = "SELECT * FROM player_status WHERE player_uuid = ?";
                        try (PreparedStatement pstmt = conn.prepareStatement(statusSql)) {
                            pstmt.setString(1, uuid);
                            ResultSet statusRs = pstmt.executeQuery();
                            if (statusRs.next()) {
                                playerData.put("health", statusRs.getDouble("health"));
                                playerData.put("max_health", statusRs.getDouble("max_health"));
                                playerData.put("food_level", statusRs.getInt("food_level"));
                                playerData.put("saturation", statusRs.getDouble("saturation"));
                                playerData.put("game_mode", statusRs.getString("game_mode"));
                                playerData.put("level", statusRs.getInt("level"));
                                playerData.put("exp", statusRs.getDouble("exp"));
                                playerData.put("total_experience", statusRs.getInt("total_experience"));
                                playerData.put("location", Arrays.asList(
                                    statusRs.getDouble("location_x"),
                                    statusRs.getDouble("location_y"),
                                    statusRs.getDouble("location_z")
                                ));
                                playerData.put("world", statusRs.getString("world_name"));
                                playerData.put("ping", statusRs.getInt("ping"));
                                playerData.put("last_updated", statusRs.getString("last_updated"));
                            }
                        }
                    }

                    // Get verification status
                    String verificationSql = "SELECT is_verified, verification_code FROM player_verification WHERE player_uuid = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(verificationSql)) {
                        pstmt.setString(1, uuid);
                        ResultSet verificationRs = pstmt.executeQuery();
                        if (verificationRs.next()) {
                            boolean isVerified = verificationRs.getBoolean("is_verified");
                            playerData.put("is_verified", isVerified);
                            if (!isVerified) {
                                playerData.put("verification_code", verificationRs.getString("verification_code"));
                            }
                        } else {
                            playerData.put("is_verified", false);
                        }
                    }
                    
                    playersData.add(playerData);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all players data: " + e.getMessage());
        }
        return playersData;
    }

    public UUID getPlayerUuidByName(String playerName) {
        String sql = "SELECT player_uuid FROM player_sessions WHERE player_name = ? ORDER BY login_time DESC LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("player_uuid"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player UUID by name: " + e.getMessage());
        }
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
        }
    }

    public String getVerificationCode(UUID playerUuid) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT verification_code FROM player_verification WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("verification_code");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error getting verification code for player " + playerUuid + ": " + e.getMessage());
        }
        return null;
    }

    public boolean isPlayerVerified(UUID playerUuid) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT is_verified FROM player_verification WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_verified");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error checking verification status for player " + playerUuid + ": " + e.getMessage());
        }
        return false;
    }

    public void saveVerificationCode(UUID playerUuid, String code) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO player_verification (player_uuid, verification_code, is_verified, created_at) VALUES (?, ?, false, CURRENT_TIMESTAMP)")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, code);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error saving verification code for player " + playerUuid + ": " + e.getMessage());
        }
    }

    public void setPlayerVerified(UUID playerUuid, boolean verified) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE player_verification SET is_verified = ?, verified_at = CURRENT_TIMESTAMP WHERE player_uuid = ?")) {
            stmt.setBoolean(1, verified);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error updating verification status for player " + playerUuid + ": " + e.getMessage());
        }
    }

    public void updatePlayerStats(UUID playerUuid, Map<String, Object> stats) {
        try (Connection conn = getConnection()) {
            // Update player statistics
            String sql = "INSERT OR REPLACE INTO player_statistics (" +
                        "player_uuid, blocks_broken, blocks_placed, deaths, player_kills, " +
                        "mob_kills, jumps, distance_walked, distance_sprinted, distance_swum, " +
                        "distance_flown, total_distance, damage_taken, damage_dealt, fish_caught, " +
                        "animals_bred, items_crafted, items_dropped, food_eaten, time_played_ticks, " +
                        "time_played_hours, last_updated) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, (int) stats.getOrDefault("blocks_broken", 0));
                stmt.setInt(3, (int) stats.getOrDefault("blocks_placed", 0));
                stmt.setInt(4, (int) stats.getOrDefault("deaths", 0));
                stmt.setInt(5, (int) stats.getOrDefault("player_kills", 0));
                stmt.setInt(6, (int) stats.getOrDefault("mob_kills", 0));
                stmt.setInt(7, (int) stats.getOrDefault("jumps", 0));
                stmt.setInt(8, (int) stats.getOrDefault("distance_walked", 0));
                stmt.setInt(9, (int) stats.getOrDefault("distance_sprinted", 0));
                stmt.setInt(10, (int) stats.getOrDefault("distance_swum", 0));
                stmt.setInt(11, (int) stats.getOrDefault("distance_flown", 0));
                stmt.setInt(12, (int) stats.getOrDefault("total_distance", 0));
                stmt.setInt(13, (int) stats.getOrDefault("damage_taken", 0));
                stmt.setInt(14, (int) stats.getOrDefault("damage_dealt", 0));
                stmt.setInt(15, (int) stats.getOrDefault("fish_caught", 0));
                stmt.setInt(16, (int) stats.getOrDefault("animals_bred", 0));
                stmt.setInt(17, (int) stats.getOrDefault("items_crafted", 0));
                stmt.setInt(18, (int) stats.getOrDefault("items_dropped", 0));
                stmt.setInt(19, (int) stats.getOrDefault("food_eaten", 0));
                stmt.setInt(20, (int) stats.getOrDefault("time_played_ticks", 0));
                stmt.setDouble(21, (double) stats.getOrDefault("time_played_hours", 0.0));
                
                stmt.executeUpdate();
            }

            // Update player status
            String statusSql = "INSERT OR REPLACE INTO player_status (" +
                             "player_uuid, player_name, health, max_health, food_level, " +
                             "saturation, game_mode, level, exp, total_experience, " +
                             "location_x, location_y, location_z, world_name, ping, last_updated) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, (String) stats.getOrDefault("name", ""));
                stmt.setDouble(3, (double) stats.getOrDefault("health", 20.0));
                stmt.setDouble(4, (double) stats.getOrDefault("max_health", 20.0));
                stmt.setInt(5, (int) stats.getOrDefault("food_level", 20));
                stmt.setDouble(6, (double) stats.getOrDefault("saturation", 5.0));
                stmt.setString(7, (String) stats.getOrDefault("game_mode", "SURVIVAL"));
                stmt.setInt(8, (int) stats.getOrDefault("level", 0));
                stmt.setDouble(9, (double) stats.getOrDefault("exp", 0.0));
                stmt.setInt(10, (int) stats.getOrDefault("total_experience", 0));
                
                // Handle location
                List<Double> location = (List<Double>) stats.getOrDefault("location", Arrays.asList(0.0, 64.0, 0.0));
                stmt.setDouble(11, location.get(0));
                stmt.setDouble(12, location.get(1));
                stmt.setDouble(13, location.get(2));
                
                stmt.setString(14, (String) stats.getOrDefault("world", "world"));
                stmt.setInt(15, (int) stats.getOrDefault("ping", 0));
                
                stmt.executeUpdate();
            }

            // Update last online time in players table
            String lastOnlineSql = "INSERT OR REPLACE INTO players (uuid, name, last_online) VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement stmt = conn.prepareStatement(lastOnlineSql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, (String) stats.getOrDefault("name", ""));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating player stats: " + e.getMessage());
        }
    }
}