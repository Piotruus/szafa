package pl.szafa;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import pl.szafa.Cosmetic.Kind;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Wirtualne kostiumy: nie ruszamy ekwipunku gracza, tylko wysylamy innym
 * (i opcjonalnie jemu) falszywe pakiety wyposazenia z modelem zbroi z resource packa.
 */
public final class CostumeManager {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final SzafaPlugin plugin;
    private final Map<String, Map<EquipmentSlot, ItemStack>> cache = new HashMap<>();

    public CostumeManager(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearCache() {
        cache.clear();
    }

    private Map<EquipmentSlot, ItemStack> itemsFor(Cosmetic c) {
        return cache.computeIfAbsent(c.id, k -> {
            Map<EquipmentSlot, ItemStack> map = new EnumMap<>(EquipmentSlot.class);
            if (c.armorModel == null && c.headModel == null) {
                return map;
            }
            for (EquipmentSlot slot : SLOTS) {
                if (slot == EquipmentSlot.HEAD && c.headModel != null) {
                    // glowa = model 3D (renderowany przez klienta jak kapelusz)
                    ItemStack head = new ItemStack(Material.PAPER);
                    ItemMeta headMeta = head.getItemMeta();
                    headMeta.setItemModel(c.headModel);
                    head.setItemMeta(headMeta);
                    map.put(slot, head);
                    continue;
                }
                if (c.armorModel == null) {
                    continue;
                }
                Material mat = switch (slot) {
                    case HEAD -> Material.IRON_HELMET;
                    case CHEST -> Material.IRON_CHESTPLATE;
                    case LEGS -> Material.IRON_LEGGINGS;
                    default -> Material.IRON_BOOTS;
                };
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                EquippableComponent eq = meta.getEquippable();
                eq.setSlot(slot);
                eq.setModel(c.armorModel);
                meta.setEquippable(eq);
                item.setItemMeta(meta);
                map.put(slot, item);
            }
            return map;
        });
    }

    /** Wysyla kostium wszystkim, ktorzy widza gracza. */
    public void refresh(Player target) {
        Cosmetic c = plugin.activeOf(target, Kind.COSTUME);
        if (c == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> items = itemsFor(c);
        if (items.isEmpty()) {
            return;
        }
        for (Player viewer : target.getTrackedBy()) {
            viewer.sendEquipmentChange(target, items);
        }
        if (plugin.showCostumeToSelf()) {
            target.sendEquipmentChange(target, items);
        }
    }

    /** Wysyla kostium jednemu widzowi (gdy zaczyna widziec gracza). */
    public void sendTo(Player viewer, Player target) {
        Cosmetic c = plugin.activeOf(target, Kind.COSTUME);
        if (c == null) {
            return;
        }
        Map<EquipmentSlot, ItemStack> items = itemsFor(c);
        if (!items.isEmpty()) {
            viewer.sendEquipmentChange(target, items);
        }
    }

    /** Przywraca prawdziwy wyglad zbroi po zdjeciu kostiumu. */
    public void restore(Player target) {
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack it = target.getInventory().getItem(slot);
            real.put(slot, it == null ? new ItemStack(Material.AIR) : it.clone());
        }
        for (Player viewer : target.getTrackedBy()) {
            viewer.sendEquipmentChange(target, real);
        }
        target.sendEquipmentChange(target, real);
    }
}
