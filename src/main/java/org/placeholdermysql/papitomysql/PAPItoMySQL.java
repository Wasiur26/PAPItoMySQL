package org.placeholdermysql.papitomysql;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public final class PAPItoMySQL extends JavaPlugin implements Listener{
    private Connection connection;
    private String tablename;

    @Override
    public void onEnable() {
        getLogger().log(Level.INFO, "Starting PAPItoMySQL");
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getLogger().log(Level.INFO, "Config file not found, creating a new one");
            getConfig().options().copyDefaults(true);
            saveDefaultConfig();
        }

        getLogger().log(Level.INFO, "Loading configuration");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String host = config.getString("mysql.host");
        int port = config.getInt("mysql.port");
        String database = config.getString("mysql.database");
        String username = config.getString("mysql.username");
        String password = config.getString("mysql.password");
        tablename = config.getString("mysql.tablename");

        try {
            getLogger().log(Level.INFO, "Connecting to MySQL");
            openConnection(host, port, database, username, password);
            createTable(tablename);
            getLogger().log(Level.INFO, "Successfully connected to MySQL");
        } catch (SQLException | ClassNotFoundException e) {
            getLogger().log(Level.SEVERE, "Error connecting to MySQL", e);
        }

        getLogger().log(Level.INFO, "Registering events");
        getServer().getPluginManager().registerEvents(this, this);

        int intervalTicks = 20 * 60 * 5;
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePlayerData();
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().log(Level.INFO, "Stopping PAPItoMySQL");
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Error closing MySQL connection", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getLogger().log(Level.INFO, "Player joined: " + event.getPlayer().getName());
        handlePlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        getLogger().log(Level.INFO, "Player quit: " + event.getPlayer().getName());
        handlePlayerData(event.getPlayer());
    }

    public void handlePlayerData(Player player){
        FileConfiguration config = getConfig();
        ConfigurationSection placeholders = config.getConfigurationSection("placeholders");
        if (placeholders == null) {
            getLogger().log(Level.WARNING, "No placeholders found in config, skipping player data update");
            return;
        }
        for(String placeholder : placeholders.getKeys(false)) {
            String value = config.getString("placeholders." + placeholder);
            getLogger().log(Level.INFO, value);
            assert value != null;
            value = PlaceholderAPI.setPlaceholders(player, value);
            getLogger().log(Level.INFO, value);
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + tablename + " (uuid, name, " + placeholder + ") VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, " + placeholder + " = ?")) {
                statement.setString(1, player.getUniqueId().toString());
                statement.setString(2, player.getName());
                statement.setString(3, value);
                statement.setString(4, player.getName());
                statement.setString(5, value);
                statement.executeUpdate();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error updating player data", e);
            }
        }
    }

    public void updatePlayerData() {
        getServer().getOnlinePlayers().forEach(player -> {

        });
    }

    public void openConnection(String host, int port, String database, String username, String password) throws SQLException, ClassNotFoundException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        synchronized (this) {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
        }
    }

    public void createTable(String tableName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            FileConfiguration config = getConfig();
            ConfigurationSection placeholders = config.getConfigurationSection("placeholders");
            if (placeholders == null) {
                getLogger().log(Level.WARNING, "No placeholders found in config, skipping table creation");
                return;
            }
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16))");
            for(String placeholder : placeholders.getKeys(false)) {
                statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN IF NOT EXISTS " + placeholder + " VARCHAR(255)");
            }
        }
    }
}
