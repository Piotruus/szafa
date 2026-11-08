package pl.szafa;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fizyczne przedmioty: zwierzatko, kostium i zwoj przedluzenia (rozpoznawane po PDC). */
public final class Items {

    public record Parsed(String type, String id, int days) {
    }

    public static final String TYPE_PET = "pet";
    public static final String TYPE_COSTUME = "costume";
    public static final String TYPE_SCROLL = "scroll";

    private final NamespacedKey kType;
    private final NamespacedKey kId;
    private final NamespacedKey kDays;

    private String scrollName = "&6&lZWÓJ PRZEDŁUŻENIA &8[&e{days} dni&8]";
    private List<String> scrollLore = List.of();
    private final Map<Integer, NamespacedKey> scrollModels = new HashMap<>();
    private NamespacedKey defaultScrollModel;

    public Items(SzafaPlugin plugin) {
        this.kType = new NamespacedKey(plugin, "type");
        this.kId = new NamespacedKey(plugin, "id");
        this.kDays = new NamespacedKey(plugin, "days");
    }

    public void reload(ConfigurationSection scroll) {
        scrollModels.clear();
        defaultScrollModel = null;
        if (scroll == null) {
            return;
        }
        scrollName = scroll.getString("name", scrollName);
        scrollLore = scroll.getStringList("lore");
        defaultScrollModel = key(scroll.getString("default-model", ""));
        ConfigurationSection models = scroll.getConfigurationSection("models");
        if (models != null) {
            for (String k : models.getKeys(false)) {
                try {
                    NamespacedKey nk = key(models.getString(k, ""));
                    if (nk != null) {
                        scrollModels.put(Integer.parseInt(k), nk);
                    }
                } catch (NumberFormatException ignored) {
                    // zly klucz - pomijamy
                }
            }
        }
    }

    private static NamespacedKey key(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return NamespacedKey.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ tworzenie

    public ItemStack cosmeticItem(Cosmetic c, int days, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        if (c.itemModel != null) {
            m.setItemModel(c.itemModel);
        }
        m.displayName(Text.c(c.name));

        List<Component> lore = new ArrayList<>();
        for (String line : c.description) {
            lore.add(Text.c(line));
        }
        lore.add(Component.empty());
        if (days <= 0) {
            lore.add(Text.c("&7Czas działania: &aBEZ LIMITU"));
        } else {
            lore.add(Text.c("&7Czas działania: &f" + days + " dni &8(liczony od założenia)"));
        }
        lore.add(Component.empty());
        lore.add(Text.c("&5◆ &e&lKLIKNIJ PPM &fABY ZAŁOŻYĆ!"));
        lore.add(Component.empty());
        lore.add(Text.c(c.rarity));
        m.lore(lore);

        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(kType, PersistentDataType.STRING, c.kind == Cosmetic.Kind.PET ? TYPE_PET : TYPE_COSTUME);
        pdc.set(kId, PersistentDataType.STRING, c.id);
        pdc.set(kDays, PersistentDataType.INTEGER, days);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack scrollItem(int days, int amount) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        NamespacedKey model = scrollModels.getOrDefault(days, defaultScrollModel);
        if (model != null) {
            m.setItemModel(model);
        }
        m.displayName(Text.c(scrollName.replace("{days}", String.valueOf(days))));
        List<Component> lore = new ArrayList<>();
        for (String line : scrollLore) {
            lore.add(Text.c(line.replace("{days}", String.valueOf(days))));
        }
        m.lore(lore);
        m.setEnchantmentGlintOverride(true);

        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(kType, PersistentDataType.STRING, TYPE_SCROLL);
        pdc.set(kDays, PersistentDataType.INTEGER, days);
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ rozpoznawanie

    /** Zwraca dane itemu albo null, jesli to nie jest przedmiot z tego pluginu. */
    public Parsed parse(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String type = pdc.get(kType, PersistentDataType.STRING);
        if (type == null) {
            return null;
        }
        String id = pdc.get(kId, PersistentDataType.STRING);
        Integer days = pdc.get(kDays, PersistentDataType.INTEGER);
        return new Parsed(type, id == null ? "" : id, days == null ? 0 : days);
    }
}
