package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Prosty zapis do data.yml. */
public final class DataStore {

    private final SzafaPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private boolean dirty;

    public DataStore(SzafaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            PlayerData d = new PlayerData();
            readMap(s.getConfigurationSection("pets"), d.pets);
            readMap(s.getConfigurationSection("costumes"), d.costumes);
            d.activePet = s.getString("active-pet");
            d.activeCostume = s.getString("active-costume");
            players.put(id, d);
        }
    }

    private static void readMap(ConfigurationSection s, Map<String, Long> out) {
        if (s == null) {
            return;
        }
        for (String k : s.getKeys(false)) {
            out.put(k, s.getLong(k));
        }
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    public void markDirty() {
        dirty = true;
    }

    public void flushAsyncIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        String text = snapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(text));
    }

    public void flushNow() {
        dirty = false;
        write(snapshot());
    }

    private String snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            String base = "players." + e.getKey();
            PlayerData d = e.getValue();
            d.pets.forEach((id, exp) -> yaml.set(base + ".pets." + id, exp));
            d.costumes.forEach((id, exp) -> yaml.set(base + ".costumes." + id, exp));
            yaml.set(base + ".active-pet", d.activePet);
            yaml.set(base + ".active-costume", d.activeCostume);
        }
        return yaml.saveToString();
    }

    private synchronized void write(String text) {
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udalo sie zapisac data.yml: " + ex.getMessage());
        }
    }
}
