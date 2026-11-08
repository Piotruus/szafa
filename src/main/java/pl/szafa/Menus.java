package pl.szafa;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.szafa.Cosmetic.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Menus implements Listener {

    // uklad glownego okna (54 sloty): zwierzatko po lewej, kostium po prawej
    private static final int PET_SLOT = 10;
    private static final int COSTUME_SLOT = 16;
    private static final int PET_LIST_SLOT = 19;
    private static final int COSTUME_LIST_SLOT = 25;
    private static final int PET_CLOCK_SLOT = 28;
    private static final int COSTUME_CLOCK_SLOT = 34;
    private static final int BACK_SLOT = 49;

    // Y = zolty naroznik, O = pomaranczowy, L = jasnoszary, G = szary, . = tlo
    private static final String[] LAYOUT = {
            "YOLLGLLOY",
            "O.......O",
            "L.......L",
            "L.......L",
            "O.......O",
            "YOLLGLLOY"
    };

    private static final List<Integer> LIST_SLOTS = new ArrayList<>();

    static {
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                LIST_SLOTS.add(row * 9 + col);
            }
        }
    }

    private static final NamespacedKey PET_PLACEHOLDER = NamespacedKey.fromString("szafa:pet_placeholder");
    private static final NamespacedKey COSTUME_PLACEHOLDER = NamespacedKey.fromString("szafa:kostium_placeholder");

    /** Holder oznaczajacy nasze GUI. kind == null => glowne okno SZAFA. */
    private static final class Holder implements InventoryHolder {
        final Kind kind;
        Inventory inv;

        Holder(Kind kind) {
            this.kind = kind;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private enum Mode {SLOT, LIST}

    private final SzafaPlugin plugin;

    public Menus(SzafaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ otwieranie

    public void openMain(Player p) {
        Holder h = new Holder(null);
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&a&lSZAFA"));
        h.inv = inv;
        renderMain(p, inv);
        p.openInventory(inv);
    }

    public void openList(Player p, Kind kind) {
        Holder h = new Holder(kind);
        String title = kind == Kind.PET ? "&a&lLista Petów" : "&6&lLista Kostiumów";
        Inventory inv = Bukkit.createInventory(h, 54, Text.c(title));
        h.inv = inv;
        renderList(p, inv, kind);
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ rysowanie

    private void renderMain(Player p, Inventory inv) {
        fillFrame(inv);
        inv.setItem(PET_SLOT, slotItem(p, Kind.PET));
        inv.setItem(COSTUME_SLOT, slotItem(p, Kind.COSTUME));
        inv.setItem(PET_LIST_SLOT, listBook(p, Kind.PET));
        inv.setItem(COSTUME_LIST_SLOT, listBook(p, Kind.COSTUME));
        inv.setItem(PET_CLOCK_SLOT, clock(p, Kind.PET));
        inv.setItem(COSTUME_CLOCK_SLOT, clock(p, Kind.COSTUME));
    }

    private void renderList(Player p, Inventory inv, Kind kind) {
        fillFrame(inv);
        List<Cosmetic> defs = new ArrayList<>(plugin.defs(kind));
        for (int i = 0; i < defs.size() && i < LIST_SLOTS.size(); i++) {
            inv.setItem(LIST_SLOTS.get(i), listIcon(p, defs.get(i)));
        }
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta m = back.getItemMeta();
        m.displayName(Text.c("&cPowrót"));
        back.setItemMeta(m);
        inv.setItem(BACK_SLOT, back);
    }

    private void fillFrame(Inventory inv) {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                inv.setItem(row * 9 + col, pane(LAYOUT[row].charAt(col)));
            }
        }
    }

    private ItemStack pane(char ch) {
        Material mat = switch (ch) {
            case 'Y' -> Material.YELLOW_STAINED_GLASS_PANE;
            case 'O' -> Material.ORANGE_STAINED_GLASS_PANE;
            case 'L' -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    // ---- sloty aktywnego zwierzatka / kostiumu

    private ItemStack slotItem(Player p, Kind kind) {
        Cosmetic active = plugin.activeOf(p, kind);
        if (active != null) {
            return icon(p, active, Mode.SLOT);
        }
        if (kind == Kind.PET) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            if (PET_PLACEHOLDER != null) {
                m.setItemModel(PET_PLACEHOLDER);
            }
            m.displayName(Text.c("&cBrak aktywnego peta"));
            m.lore(List.of(
                    Text.c("&7Weź item zwierzątka do ręki i kliknij &fPPM&7,"),
                    Text.c("&7albo wybierz je z &aListy Petów&7.")));
            it.setItemMeta(m);
            return it;
        }
        ItemStack it = new ItemStack(Material.ARMOR_STAND);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c("&cBrak aktywnego kostiumu"));
        m.lore(List.of(
                Text.c("&7Weź item kostiumu do ręki i kliknij &fPPM&7,"),
                Text.c("&7albo wybierz go z &6Listy Kostiumów&7.")));
        it.setItemMeta(m);
        return it;
    }

    // ---- ksiazki (listy) i zegary (przedluzanie)

    private ItemStack listBook(Player p, Kind kind) {
        ItemStack it = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(kind == Kind.PET ? "&a&lLISTA PETÓW" : "&6&lLISTA KOSTIUMÓW"));

        int total = plugin.defs(kind).size();
        int valid = plugin.validCount(p, kind);
        Cosmetic active = plugin.activeOf(p, kind);
        long nearest = plugin.nearestExpiry(p, kind);

        List<Component> lore = new ArrayList<>();
        lore.add(Text.c("&7Kliknij, aby otworzyć listę."));
        lore.add(Component.empty());
        lore.add(Text.c("&7Posiadasz: &f" + valid + " &7z &f" + total));
        lore.add(Text.c("&7Aktywne: " + (active != null ? "&f" + active.name : "&cbrak")));
        if (nearest > 0) {
            lore.add(Text.c("&7Najbliższe wygaśnięcie: &f" + plugin.format(nearest)
                    + " &8(" + plugin.remaining(nearest) + ")"));
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack clock(Player p, Kind kind) {
        ItemStack it = new ItemStack(Material.CLOCK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.c(kind == Kind.PET ? "&e&lPRZEDŁUŻ PETA" : "&e&lPRZEDŁUŻ KOSTIUM"));

        List<Component> lore = new ArrayList<>();
        Cosmetic active = plugin.activeOf(p, kind);
        if (active == null) {
            lore.add(Text.c("&c✘ &fNie masz aktywnego " + (kind == Kind.PET ? "peta" : "kostiumu")));
        } else {
            Long exp = SzafaPlugin.owned(plugin.data().get(p.getUniqueId()), kind).get(active.id);
            lore.add(Text.c("&7Aktywne: &f" + active.name));
            if (exp != null && exp == Long.MAX_VALUE) {
                lore.add(Text.c("&aDziała bez limitu czasu."));
            } else if (exp != null) {
                lore.add(Text.c("&7Wygasa: &f" + plugin.format(exp)));
                lore.add(Text.c("&7Pozostało: &f" + plugin.remaining(exp)));
            }
            lore.add(Component.empty());
            Map<Integer, Integer> scrolls = plugin.scrollCounts(p);
            if (scrolls.isEmpty()) {
                lore.add(Text.c("&c✘ &fBrak &6Zwojów przedłużenia &fw ekwipunku."));
            } else {
                lore.add(Text.c("&7Twoje zwoje:"));
                scrolls.forEach((days, count) ->
                        lore.add(Text.c("&8→ &f" + days + " dni &7x" + count)));
                lore.add(Component.empty());
                lore.add(Text.c("&e&lLPM &7- użyj najmniejszego zwoju"));
                lore.add(Text.c("&e&lPPM &7- użyj największego zwoju"));
            }
        }
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    // ---- ikony w listach

    private ItemStack listIcon(Player p, Cosmetic c) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        Long exp = SzafaPlugin.owned(d, c.kind).get(c.id);
        if (exp == null) {
            return lockedIcon(c);
        }
        return icon(p, c, Mode.LIST);
    }

    /** Nieposiadane: szary placeholder z nazwa i opisem (podglad co daje). */
    private ItemStack lockedIcon(Cosmetic c) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        NamespacedKey ph = c.kind == Kind.PET ? PET_PLACEHOLDER : COSTUME_PLACEHOLDER;
        if (ph != null) {
            m.setItemModel(ph);
        }
        m.displayName(Text.c(c.name));
        List<Component> lore = new ArrayList<>();
        for (String line : c.description) {
            lore.add(Text.c(line));
        }
        lore.add(Component.empty());
        lore.add(Text.c("&c✘ &fNie posiadasz tego " + SzafaPlugin.noun(c.kind) + "."));
        lore.add(Text.c("&7Zdobądź item i kliknij go &fPPM&7."));
        lore.add(Component.empty());
        lore.add(Text.c(c.rarity));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack icon(Player p, Cosmetic c, Mode mode) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (c.itemModel != null) {
            m.setItemModel(c.itemModel);
        }
        m.displayName(Text.c(c.name));

        PlayerData d = plugin.data().get(p.getUniqueId());
        Long exp = SzafaPlugin.owned(d, c.kind).get(c.id);
        boolean active = c.id.equals(SzafaPlugin.activeId(d, c.kind));
        boolean valid = exp != null && exp > System.currentTimeMillis();
        String noun = c.kind == Kind.PET ? "ZWIERZĄTKO" : "KOSTIUM";

        List<Component> lore = new ArrayList<>();
        for (String line : c.description) {
            lore.add(Text.c(line));
        }
        lore.add(Component.empty());

        if (exp == null) {
            lore.add(Text.c("&c✘ &fNie posiadasz tego " + SzafaPlugin.noun(c.kind) + "."));
        } else if (!valid) {
            lore.add(Text.c("&c✘ &f" + noun + " WYGASŁO: &c" + plugin.format(exp)));
            lore.add(Text.c("&7Użyj nowego itemu, aby je odnowić."));
        } else if (exp == Long.MAX_VALUE) {
            lore.add(Text.c("&a✔ &f" + noun + " &aBEZ LIMITU CZASU"));
        } else {
            lore.add(Text.c("&c✘ &f" + noun + " WYGASA: &c" + plugin.format(exp)));
            lore.add(Text.c("&7Pozostało: &f" + plugin.remaining(exp)));
        }
        lore.add(Component.empty());

        if (active && valid) {
            lore.add(Text.c("&a✔ &a&lAKTYWNE"));
        }
        if (mode == Mode.SLOT) {
            lore.add(Text.c("&e&lKLIKNIJ &7- aby zdjąć"));
        } else if (valid) {
            lore.add(Text.c(active
                    ? "&5◆ &e&lKLIKNIJ &fABY ZDJĄĆ!"
                    : "&5◆ &e&lKLIKNIJ &fABY ZAŁOŻYĆ!"));
        }
        lore.add(Component.empty());
        lore.add(Text.c(c.rarity));

        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (active && valid) {
            m.setEnchantmentGlintOverride(true);
        }
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ klikniecia

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (h.kind == null) {
            clickMain(p, h, e.getSlot(), e.getClick());
        } else {
            clickList(p, h, e.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    private void clickMain(Player p, Holder h, int slot, ClickType click) {
        switch (slot) {
            case PET_SLOT -> unequip(p, h, Kind.PET);
            case COSTUME_SLOT -> unequip(p, h, Kind.COSTUME);
            case PET_LIST_SLOT -> {
                click(p);
                openList(p, Kind.PET);
            }
            case COSTUME_LIST_SLOT -> {
                click(p);
                openList(p, Kind.COSTUME);
            }
            case PET_CLOCK_SLOT -> {
                plugin.extend(p, Kind.PET, click.isRightClick());
                renderMain(p, h.inv);
            }
            case COSTUME_CLOCK_SLOT -> {
                plugin.extend(p, Kind.COSTUME, click.isRightClick());
                renderMain(p, h.inv);
            }
            default -> {
            }
        }
    }

    private void unequip(Player p, Holder h, Kind kind) {
        if (plugin.deactivate(p, kind)) {
            plugin.msg(p, kind == Kind.PET ? "Zdjęto zwierzątko." : "Zdjęto kostium.");
            click(p);
        } else {
            plugin.msg(p, "&cNie masz aktywnego " + (kind == Kind.PET ? "peta" : "kostiumu") + ".");
        }
        renderMain(p, h.inv);
    }

    private void clickList(Player p, Holder h, int slot) {
        if (slot == BACK_SLOT) {
            click(p);
            openMain(p);
            return;
        }
        int idx = LIST_SLOTS.indexOf(slot);
        List<Cosmetic> defs = new ArrayList<>(plugin.defs(h.kind));
        if (idx < 0 || idx >= defs.size()) {
            return;
        }
        Cosmetic c = defs.get(idx);
        PlayerData d = plugin.data().get(p.getUniqueId());
        Long exp = SzafaPlugin.owned(d, c.kind).get(c.id);
        if (exp == null) {
            plugin.msg(p, "&cNie posiadasz tego " + SzafaPlugin.noun(c.kind) + ". Zdobądź item i kliknij go &fPPM&c.");
            return;
        }
        if (exp <= System.currentTimeMillis()) {
            plugin.msg(p, "&cTo " + SzafaPlugin.noun(c.kind) + " już wygasło. Użyj nowego itemu.");
            return;
        }
        boolean active = c.id.equals(SzafaPlugin.activeId(d, c.kind));
        if (active) {
            plugin.deactivate(p, c.kind);
            plugin.msg(p, "Zdjęto: &f" + c.name);
        } else {
            plugin.activate(p, c, true);
        }
        click(p);
        renderList(p, h.inv, h.kind);
    }

    private void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}
