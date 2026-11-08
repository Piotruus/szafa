package pl.szafa;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import pl.szafa.Cosmetic.Kind;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class SzafaPlugin extends JavaPlugin {

    public static final long DAY_MS = 86_400_000L;
    public static final long HOUR_MS = 3_600_000L;

    private final Map<String, Cosmetic> pets = new LinkedHashMap<>();
    private final Map<String, Cosmetic> costumes = new LinkedHashMap<>();

    private DataStore data;
    private Items items;
    private PetManager petManager;
    private CostumeManager costumeManager;
    private Menus menus;
    private DateTimeFormatter dateFormat;
    private boolean showCostumeToSelf;
    private float petScale = 0.5f;
    private float petYawOffset = 0f;
    private boolean petNametag = true;
    private int joinWarningHours = 24;
    private int maintenanceTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new Items(this);
        loadDefinitions();

        data = new DataStore(this);
        petManager = new PetManager(this);
        costumeManager = new CostumeManager(this);
        menus = new Menus(this);

        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);

        getCommand("szafa").setExecutor(new SzafaCommand(this));
        AdminCommand admin = new AdminCommand(this);
        getCommand("szafaadmin").setExecutor(admin);
        getCommand("szafaadmin").setTabCompleter(admin);

        Bukkit.getScheduler().runTaskTimer(this, petManager::tick, 20L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, this::maintenance, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.removeAll();
        }
        if (data != null) {
            data.flushNow();
        }
    }

    // ------------------------------------------------------------------ config

    public void loadDefinitions() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        ZoneId zone;
        try {
            zone = ZoneId.of(cfg.getString("timezone", "Europe/Warsaw"));
        } catch (Exception ex) {
            zone = ZoneId.systemDefault();
        }
        dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(zone);
        showCostumeToSelf = cfg.getBoolean("show-costume-to-self", false);
        petScale = (float) cfg.getDouble("pet-scale", 0.5);
        petYawOffset = (float) cfg.getDouble("pet-yaw-offset", 0.0);
        petNametag = cfg.getBoolean("pet-nametag", true);
        joinWarningHours = cfg.getInt("join-warning-hours", 24);

        pets.clear();
        costumes.clear();
        loadSection(cfg.getConfigurationSection("pets"), Kind.PET, pets);
        loadSection(cfg.getConfigurationSection("costumes"), Kind.COSTUME, costumes);
        items.reload(cfg.getConfigurationSection("scroll"));
        if (costumeManager != null) {
            costumeManager.clearCache();
        }
    }

    private void loadSection(ConfigurationSection root, Kind kind, Map<String, Cosmetic> out) {
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) {
                out.put(id, Cosmetic.load(kind, id, s, getLogger()));
            }
        }
    }

    public void reloadAll() {
        loadDefinitions();
        petManager.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            costumeManager.refresh(p);
        }
    }

    // ------------------------------------------------------------------ accessors

    public DataStore data() {
        return data;
    }

    public Items items() {
        return items;
    }

    public PetManager petManager() {
        return petManager;
    }

    public CostumeManager costumeManager() {
        return costumeManager;
    }

    public Menus menus() {
        return menus;
    }

    public boolean showCostumeToSelf() {
        return showCostumeToSelf;
    }

    public float petScale() {
        return petScale;
    }

    public float petYawOffset() {
        return petYawOffset;
    }

    public boolean petNametag() {
        return petNametag;
    }

    public Collection<Cosmetic> defs(Kind kind) {
        return (kind == Kind.PET ? pets : costumes).values();
    }

    public Cosmetic def(Kind kind, String id) {
        if (id == null) {
            return null;
        }
        return (kind == Kind.PET ? pets : costumes).get(id);
    }

    public String format(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "BEZ LIMITU";
        }
        return dateFormat.format(Instant.ofEpochMilli(millis));
    }

    /** Pozostaly czas w ladnej formie: "6d 23h", "5h 12m", "12m". */
    public String remaining(long expiry) {
        if (expiry == Long.MAX_VALUE) {
            return "bez limitu";
        }
        long diff = expiry - System.currentTimeMillis();
        if (diff <= 0) {
            return "wygasło";
        }
        long days = diff / DAY_MS;
        long hours = (diff % DAY_MS) / HOUR_MS;
        long minutes = (diff % HOUR_MS) / 60_000L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    public static Map<String, Long> owned(PlayerData d, Kind kind) {
        return kind == Kind.PET ? d.pets : d.costumes;
    }

    public static String activeId(PlayerData d, Kind kind) {
        return kind == Kind.PET ? d.activePet : d.activeCostume;
    }

    public static String noun(Kind kind) {
        return kind == Kind.PET ? "zwierzątko" : "kostium";
    }

    public void msg(CommandSender to, String text) {
        to.sendMessage(Text.c(Text.PREFIX + text));
    }

    // ------------------------------------------------------------------ stan gracza

    /** Aktywne i niewygasniete zwierzatko / kostium gracza (albo null). */
    public Cosmetic activeOf(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        String id = activeId(d, kind);
        if (id == null) {
            return null;
        }
        Long exp = owned(d, kind).get(id);
        if (exp == null || exp <= System.currentTimeMillis()) {
            return null;
        }
        return def(kind, id);
    }

    public List<Cosmetic> activeList(Player p) {
        List<Cosmetic> out = new ArrayList<>(2);
        Cosmetic pet = activeOf(p, Kind.PET);
        Cosmetic costume = activeOf(p, Kind.COSTUME);
        if (pet != null) {
            out.add(pet);
        }
        if (costume != null) {
            out.add(costume);
        }
        return out;
    }

    public List<Bonuses> bonusesOf(Player p) {
        List<Bonuses> out = new ArrayList<>(2);
        for (Cosmetic c : activeList(p)) {
            out.add(c.bonuses);
        }
        return out;
    }

    /** Ile niewygaslych sztuk gracz posiada w szafie. */
    public int validCount(Player p, Kind kind) {
        long now = System.currentTimeMillis();
        int n = 0;
        for (Map.Entry<String, Long> e : owned(data.get(p.getUniqueId()), kind).entrySet()) {
            if (e.getValue() > now && def(kind, e.getKey()) != null) {
                n++;
            }
        }
        return n;
    }

    /** Najblizsza data wygasniecia posiadanych (niewygaslych) sztuk, albo -1. */
    public long nearestExpiry(Player p, Kind kind) {
        long now = System.currentTimeMillis();
        long best = -1;
        for (Map.Entry<String, Long> e : owned(data.get(p.getUniqueId()), kind).entrySet()) {
            long exp = e.getValue();
            if (exp > now && exp != Long.MAX_VALUE && def(kind, e.getKey()) != null && (best < 0 || exp < best)) {
                best = exp;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ aktywacja

    public void activate(Player p, Cosmetic c, boolean announce) {
        PlayerData d = data.get(p.getUniqueId());
        Cosmetic old = activeOf(p, c.kind);
        if (old != null) {
            clearEffects(p, old.bonuses);
        }
        if (c.kind == Kind.PET) {
            d.activePet = c.id;
            petManager.remove(p);
        } else {
            d.activeCostume = c.id;
        }
        data.markDirty();
        applyEffects(p);
        if (c.kind == Kind.COSTUME) {
            costumeManager.refresh(p);
        }
        if (announce) {
            msg(p, "Założono: &f" + c.name);
        }
    }

    public boolean deactivate(Player p, Kind kind) {
        PlayerData d = data.get(p.getUniqueId());
        String id = activeId(d, kind);
        if (id == null) {
            return false;
        }
        Cosmetic cur = def(kind, id);
        if (kind == Kind.PET) {
            d.activePet = null;
            petManager.remove(p);
        } else {
            d.activeCostume = null;
        }
        if (cur != null) {
            clearEffects(p, cur.bonuses);
        }
        if (kind == Kind.COSTUME) {
            costumeManager.restore(p);
        }
        data.markDirty();
        return true;
    }

    // ------------------------------------------------------------------ uzycie przedmiotow

    /** Prawy klik z itemem pluginu w rece. */
    public void useItem(Player p, Items.Parsed pr) {
        switch (pr.type()) {
            case Items.TYPE_PET -> useCosmeticItem(p, Kind.PET, pr);
            case Items.TYPE_COSTUME -> useCosmeticItem(p, Kind.COSTUME, pr);
            case Items.TYPE_SCROLL -> {
                msg(p, "Zwoju użyjesz w &f/szafa&7: kliknij &6zegar &7pod zwierzątkiem albo kostiumem.");
                menus.openMain(p);
            }
            default -> {
            }
        }
    }

    private void useCosmeticItem(Player p, Kind kind, Items.Parsed pr) {
        Cosmetic c = def(kind, pr.id());
        if (c == null) {
            msg(p, "&cTen przedmiot jest nieznany (usunięty z konfiguracji).");
            return;
        }
        PlayerData d = data.get(p.getUniqueId());
        Map<String, Long> owned = owned(d, kind);
        long now = System.currentTimeMillis();
        Long cur = owned.get(c.id);
        if (cur != null && cur > now) {
            msg(p, "&cMasz już " + noun(kind) + " &f" + c.name + " &cdo &f" + format(cur)
                    + "&c. Użyj &6Zwoju przedłużenia&c w &f/szafa&c.");
            return;
        }
        long exp = pr.days() <= 0 ? Long.MAX_VALUE : now + pr.days() * DAY_MS;
        owned.put(c.id, exp);
        consumeOneFromMainHand(p);
        activate(p, c, false);
        msg(p, "&aZałożono: &f" + c.name + " &7- wygasa: &f" + format(exp) + " &8(" + remaining(exp) + ")");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
    }

    private void consumeOneFromMainHand(Player p) {
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (inHand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
            p.getInventory().setItemInMainHand(inHand);
        }
    }

    /** Zliczone zwoje w ekwipunku: dni -> ilosc (posortowane rosnaco). */
    public Map<Integer, Integer> scrollCounts(Player p) {
        Map<Integer, Integer> out = new TreeMap<>();
        for (ItemStack s : p.getInventory().getStorageContents()) {
            Items.Parsed pr = items.parse(s);
            if (pr != null && pr.type().equals(Items.TYPE_SCROLL) && pr.days() > 0) {
                out.merge(pr.days(), s.getAmount(), Integer::sum);
            }
        }
        return out;
    }

    /** Przedluza aktywne zwierzatko/kostium zwojem z ekwipunku (najmniejszym albo najwiekszym). */
    public boolean extend(Player p, Kind kind, boolean largest) {
        String what = kind == Kind.PET ? "peta" : "kostiumu";
        Cosmetic c = activeOf(p, kind);
        if (c == null) {
            msg(p, "&cNie masz aktywnego " + what + ".");
            return false;
        }
        PlayerData d = data.get(p.getUniqueId());
        Long exp = owned(d, kind).get(c.id);
        if (exp == null) {
            return false;
        }
        if (exp == Long.MAX_VALUE) {
            msg(p, "&cTo " + noun(kind) + " działa bez limitu czasu - nie trzeba przedłużać.");
            return false;
        }

        int bestSlot = -1;
        int bestDays = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            Items.Parsed pr = items.parse(contents[i]);
            if (pr == null || !pr.type().equals(Items.TYPE_SCROLL) || pr.days() <= 0) {
                continue;
            }
            if (bestSlot < 0 || (largest ? pr.days() > bestDays : pr.days() < bestDays)) {
                bestSlot = i;
                bestDays = pr.days();
            }
        }
        if (bestSlot < 0) {
            msg(p, "&cNie masz &6Zwoju przedłużenia &cw ekwipunku.");
            return false;
        }

        ItemStack s = p.getInventory().getItem(bestSlot);
        if (s == null || s.getAmount() <= 1) {
            p.getInventory().setItem(bestSlot, null);
        } else {
            s.setAmount(s.getAmount() - 1);
            p.getInventory().setItem(bestSlot, s);
        }

        long newExp = exp + bestDays * DAY_MS;
        owned(d, kind).put(c.id, newExp);
        data.markDirty();
        msg(p, "&aPrzedłużono &f" + c.name + " &ao &f" + bestDays + " dni&a. Nowa data: &f"
                + format(newExp) + " &8(" + remaining(newExp) + ")");
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------ admin

    /** Nadaje bezposrednio do szafy (bez itemu); czas liczony od teraz. days == null = bez limitu. */
    public long grant(UUID uuid, Cosmetic c, Integer days) {
        PlayerData d = data.get(uuid);
        Map<String, Long> map = owned(d, c.kind);
        long now = System.currentTimeMillis();
        Long cur = map.get(c.id);
        long result;
        if (days == null || (cur != null && cur == Long.MAX_VALUE)) {
            result = Long.MAX_VALUE;
        } else {
            long base = (cur != null && cur > now) ? cur : now;
            result = base + days * DAY_MS;
        }
        map.put(c.id, result);
        data.markDirty();
        return result;
    }

    public boolean take(UUID uuid, Cosmetic c) {
        PlayerData d = data.get(uuid);
        boolean had = owned(d, c.kind).remove(c.id) != null;
        Player online = Bukkit.getPlayer(uuid);
        if (c.id.equals(activeId(d, c.kind))) {
            if (online != null) {
                deactivate(online, c.kind);
            } else if (c.kind == Kind.PET) {
                d.activePet = null;
            } else {
                d.activeCostume = null;
            }
        }
        data.markDirty();
        return had;
    }

    /** Daje item do ekwipunku, nadmiar wyrzuca pod nogi. */
    public void giveItem(Player target, ItemStack item) {
        Map<Integer, ItemStack> left = target.getInventory().addItem(item);
        Location loc = target.getLocation();
        for (ItemStack rest : left.values()) {
            target.getWorld().dropItemNaturally(loc, rest);
        }
    }

    // ------------------------------------------------------------------ efekty i wygasanie

    public void applyEffects(Player p) {
        for (Cosmetic c : activeList(p)) {
            for (Bonuses.EffectSpec s : c.bonuses.effects) {
                p.addPotionEffect(new PotionEffect(s.type(), 100, s.amplifier(), true, false, true));
            }
        }
    }

    public void clearEffects(Player p, Bonuses b) {
        for (Bonuses.EffectSpec s : b.effects) {
            PotionEffect cur = p.getPotionEffect(s.type());
            if (cur != null && cur.getAmplifier() == s.amplifier() && cur.getDuration() <= 120) {
                p.removePotionEffect(s.type());
            }
        }
    }

    /** Po wejsciu: ostrzega, ze cos wkrotce wygasa. */
    public void joinCheck(Player p) {
        if (joinWarningHours <= 0) {
            return;
        }
        PlayerData d = data.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        for (Kind kind : Kind.values()) {
            String id = activeId(d, kind);
            Long exp = id == null ? null : owned(d, kind).get(id);
            Cosmetic c = def(kind, id);
            if (exp != null && c != null && exp != Long.MAX_VALUE && exp > now
                    && exp - now < joinWarningHours * HOUR_MS) {
                msg(p, "&eTwoje " + noun(kind) + " &f" + c.name + " &ewygaśnie za &f" + remaining(exp)
                        + "&e. Użyj &6Zwoju przedłużenia &ew &f/szafa&e.");
            }
        }
    }

    private void maintenance() {
        maintenanceTick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            checkExpiry(p);
            if (maintenanceTick % 2 == 0) {
                applyEffects(p);
            }
            if (maintenanceTick % 3 == 0) {
                costumeManager.refresh(p);
            }
        }
        if (maintenanceTick % 10 == 0) {
            data.flushAsyncIfDirty();
        }
    }

    private void checkExpiry(Player p) {
        PlayerData d = data.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        for (Kind kind : Kind.values()) {
            String id = activeId(d, kind);
            if (id == null) {
                continue;
            }
            Long exp = owned(d, kind).get(id);
            if (exp == null || exp <= now) {
                Cosmetic c = def(kind, id);
                deactivate(p, kind);
                msg(p, "&cTwoje " + noun(kind) + (c != null ? " &f" + c.name : "") + " &cwygasło.");
            }
        }
    }
}
