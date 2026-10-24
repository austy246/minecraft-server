package cz.minecraft.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Koruna: jeden predmet na drzitele, ktery ukazuje jeho drahokamy.
 * Kazdy drahokam dava +1 Strength (pricita se k Strength z lektvaru).
 * Drahokam kazdeho tymu existuje ve hre jen jednou.
 */
final class CrownManager implements Listener {

    private static final double STRENGTH_PER_GEM = 3.0;

    private final TeamsPlugin plugin;
    private final NamespacedKey crownKey;
    private final NamespacedKey strengthKey;

    CrownManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.crownKey = new NamespacedKey(plugin, "crown");
        this.strengthKey = new NamespacedKey(plugin, "crown_strength");
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
    }

    // --- predmet ---

    boolean isCrown(ItemStack it) {
        return it != null && it.getType() == Material.PAPER && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(crownKey, PersistentDataType.STRING);
    }

    private static String signature(List<TeamType> gems) {
        StringBuilder sb = new StringBuilder();
        for (TeamType t : gems) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(t.id);
        }
        return sb.toString();
    }

    ItemStack create(List<TeamType> gems) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Koruna", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (gems.isEmpty()) {
            lore.add(Component.text("Bez drahokamu", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Drahokamy (" + gems.size() + "/" + TeamType.values().length + "):",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            for (TeamType t : gems) {
                lore.add(Component.text("◆ " + t.display, t.color)
                        .append(Component.text("  +1 Strength", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        m.lore(lore);
        m.getPersistentDataContainer().set(crownKey, PersistentDataType.STRING, signature(gems));
        if (plugin.getConfig().getBoolean("crown.use-item-model", true)) {
            String model = gems.isEmpty() ? "crown_empty" : "crown_" + gems.get(gems.size() - 1).id;
            m.setItemModel(new NamespacedKey("teams", model));
            // Model koruny na hlave hrace (equipment asset v resource packu).
            EquippableComponent eq = m.getEquippable();
            eq.setSlot(EquipmentSlot.HEAD);
            eq.setModel(new NamespacedKey("teams", model));
            eq.setDamageOnHurt(false);
            eq.setSwappable(false);
            m.setEquippable(eq);
        } else {
            EquippableComponent eq = m.getEquippable();
            eq.setSlot(EquipmentSlot.HEAD);
            eq.setDamageOnHurt(false);
            m.setEquippable(eq);
        }
        it.setItemMeta(m);
        return it;
    }

    // --- drzitele a drahokamy ---

    /** Zvoleny kral dostane korunu a drahokam sveho tymu, pokud ho nema nikdo jiny. */
    void grantKingCrown(UUID king, TeamType team) {
        List<TeamType> list = plugin.data.gemsOrCreate(king);
        if (plugin.data.gemHolder(team) == null) {
            list.add(team);
        }
        plugin.data.save();
        Player p = Bukkit.getPlayer(king);
        if (p != null) {
            sync(p);
        }
    }

    /** Zabijak vezme obeti naposled ziskany drahokam. Obet o korunu neprijde. */
    void transferGem(Player killer, Player victim) {
        List<TeamType> vg = plugin.data.gems(victim.getUniqueId());
        if (vg == null || vg.isEmpty()) {
            return;
        }
        TeamType gem = vg.remove(vg.size() - 1);
        plugin.data.gemsOrCreate(killer.getUniqueId()).add(gem);
        plugin.data.save();
        sync(victim);
        sync(killer);
        plugin.getServer().broadcast(Component.text(killer.getName(), NamedTextColor.GOLD)
                .append(Component.text(" zabil korunovaného hráče ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), NamedTextColor.GOLD))
                .append(Component.text(" a vzal mu drahokam ", NamedTextColor.GRAY))
                .append(Component.text("◆ " + gem.display, gem.color))
                .append(Component.text("!", NamedTextColor.GRAY)));
    }

    void sweep() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            sync(p);
        }
    }

    /** Zajisti, ze hrac drzi korunu na hlave, pokud je drzitel, jinak zadnou. */
    void sync(Player p) {
        List<TeamType> gems = plugin.data.gems(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        int headIndex = 39;

        // koruna nikde jinde nez na hlave
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != headIndex && isCrown(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isCrown(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }

        ItemStack head = inv.getHelmet();
        if (gems == null) {
            if (isCrown(head)) {
                inv.setHelmet(null);
            }
        } else {
            String want = signature(gems);
            String have = isCrown(head)
                    ? head.getItemMeta().getPersistentDataContainer().get(crownKey, PersistentDataType.STRING)
                    : null;
            if (!want.equals(have)) {
                inv.setHelmet(create(gems));
                // puvodni helma jde do inventare
                if (head != null && !isCrown(head)) {
                    inv.addItem(head).values()
                            .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                }
            }
        }
        updateStrength(p, gems == null ? 0 : gems.size());
    }

    private void updateStrength(Player p, int gemCount) {
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr == null) {
            return;
        }
        double want = gemCount * STRENGTH_PER_GEM;
        AttributeModifier existing = null;
        for (AttributeModifier m : attr.getModifiers()) {
            if (m.getKey().equals(strengthKey)) {
                existing = m;
                break;
            }
        }
        if (existing != null && existing.getAmount() == want) {
            return;
        }
        if (existing != null) {
            attr.removeModifier(existing);
        }
        if (gemCount > 0) {
            attr.addModifier(new AttributeModifier(strengthKey, want,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    // --- koruna nejde zahodit, ulozit ani ukrast ---

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(this::isCrown);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isCrown(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof ArmorStand) {
            Player p = e.getPlayer();
            if (isCrown(p.getInventory().getItemInMainHand()) || isCrown(p.getInventory().getItemInOffHand())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (isCrown(e.getCurrentItem()) && e.getSlotType() == InventoryType.SlotType.ARMOR) {
            e.setCancelled(true);
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            return;
        }
        boolean inTop = e.getClickedInventory() == top;
        if (inTop && isCrown(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.isShiftClick() && !inTop && isCrown(e.getCurrentItem())) {
            e.setCancelled(true);
            return;
        }
        if (inTop && e.getClick() == ClickType.NUMBER_KEY
                && isCrown(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))) {
            e.setCancelled(true);
            return;
        }
        if (inTop && e.getClick() == ClickType.SWAP_OFFHAND
                && isCrown(e.getWhoClicked().getInventory().getItemInOffHand())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || !isCrown(e.getOldCursor())) {
            return;
        }
        int topSize = top.getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
