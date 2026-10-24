package cz.minecraft.teams;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Koruna je plovouci model, ktery hraci sedi na hlave nad helmou (ItemDisplay jako pasazer).
 * Zadny predmet v inventari ani ve slotu helmy neexistuje, takze hrac o helmu neprijde.
 * Kazdy drahokam dava +1 Strength (pricita se k Strength z lektvaru).
 * Drahokam kazdeho tymu existuje ve hre jen jednou a koruna se pri zabiti presouva i s drahokamy.
 */
final class CrownManager implements Listener {

    private static final double STRENGTH_PER_GEM = 3.0;
    /** Vyska spodku koruny nad pasazerskym bodem hrace (v blocich pri velikosti 1). */
    private static final float RING_LIFT = 0.41f;

    private final TeamsPlugin plugin;
    /** Klic stare verze, kdy byla koruna predmet. Ukliziji se zbytky. */
    private final NamespacedKey legacyCrownKey;
    private final NamespacedKey strengthKey;
    private final Map<UUID, Shown> shown = new HashMap<>();

    private record Shown(ItemDisplay display, String signature) {
    }

    CrownManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.legacyCrownKey = new NamespacedKey(plugin, "crown");
        this.strengthKey = new NamespacedKey(plugin, "crown_strength");
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
    }

    void shutdown() {
        for (Shown s : shown.values()) {
            s.display().remove();
        }
        shown.clear();
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

    private boolean isLegacyCrown(ItemStack it) {
        return it != null && it.getType() == Material.PAPER && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(legacyCrownKey, PersistentDataType.STRING);
    }

    /** Predmet, ktery se zobrazuje na hlave (model v resource packu). */
    private ItemStack displayItem(List<TeamType> gems) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Koruna", NamedTextColor.GOLD));
        String model = gems.isEmpty() ? "crown_ring_empty" : "crown_ring_" + gems.get(gems.size() - 1).id;
        m.setItemModel(new NamespacedKey("teams", model));
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

    /**
     * Zabijak vezme obeti celou korunu i s drahokamy. Koruna se nikdy nekopiruje:
     * obet o ni prijde, zabijakovi se drahokamy pridaji (nebo dostane korunu novou).
     */
    void transferCrown(Player killer, Player victim) {
        List<TeamType> vg = plugin.data.gems(victim.getUniqueId());
        if (vg == null || vg.isEmpty()) {
            return;
        }
        List<TeamType> taken = plugin.data.removeCrown(victim.getUniqueId());
        plugin.data.gemsOrCreate(killer.getUniqueId()).addAll(taken);
        plugin.data.save();
        sync(victim);
        sync(killer);
        Component gemNames = Component.empty();
        for (TeamType gem : taken) {
            gemNames = gemNames.append(Component.text(" ◆ " + gem.display, gem.color));
        }
        plugin.getServer().broadcast(Component.text(killer.getName(), NamedTextColor.GOLD)
                .append(Component.text(" zabil korunovaného hráče ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), NamedTextColor.GOLD))
                .append(Component.text(" a vzal mu korunu s drahokamy:", NamedTextColor.GRAY))
                .append(gemNames));
    }

    void sweep() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            sync(p);
        }
    }

    /** Zobrazi korunu drziteli, ostatnim ji odebere. Zbytky stare verze (predmet) uklidi. */
    void sync(Player p) {
        removeLegacyCrowns(p);
        List<TeamType> gems = plugin.data.gems(p.getUniqueId());
        updateStrength(p, gems == null ? 0 : gems.size());
        if (gems == null || p.isDead()) {
            removeDisplay(p);
            return;
        }
        ensureDisplay(p, gems);
    }

    /** Stara verze davala korunu jako predmet na hlavu a helmu schovala do inventare. Vrati ji zpet. */
    private void removeLegacyCrowns(Player p) {
        PlayerInventory inv = p.getInventory();
        boolean wasOnHead = isLegacyCrown(inv.getHelmet());
        for (int i = 0; i < inv.getSize(); i++) {
            if (isLegacyCrown(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isLegacyCrown(p.getItemOnCursor())) {
            p.setItemOnCursor(null);
        }
        if (wasOnHead && inv.getHelmet() == null) {
            for (int i = 0; i < 36; i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && Tag.ITEMS_HEAD_ARMOR.isTagged(it.getType())) {
                    inv.setHelmet(it);
                    inv.setItem(i, null);
                    break;
                }
            }
        }
    }

    private Transformation transformation(Player p) {
        AttributeInstance scaleAttr = p.getAttribute(Attribute.SCALE);
        float s = scaleAttr == null ? 1f : (float) scaleAttr.getValue();
        return new Transformation(new Vector3f(0f, RING_LIFT * s, 0f), new AxisAngle4f(),
                new Vector3f(s, s, s), new AxisAngle4f());
    }

    private void ensureDisplay(Player p, List<TeamType> gems) {
        UUID u = p.getUniqueId();
        String sig = signature(gems);
        Shown s = shown.get(u);
        if (s != null && (!s.display().isValid() || !p.getPassengers().contains(s.display()))) {
            s.display().remove();
            shown.remove(u);
            s = null;
        }
        if (s == null) {
            ItemDisplay d = p.getWorld().spawn(p.getLocation(), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(displayItem(gems));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTransformation(transformation(p));
            });
            p.addPassenger(d);
            shown.put(u, new Shown(d, sig));
            return;
        }
        if (!sig.equals(s.signature())) {
            s.display().setItemStack(displayItem(gems));
            shown.put(u, new Shown(s.display(), sig));
        }
        s.display().setTransformation(transformation(p));
    }

    private void removeDisplay(Player p) {
        Shown s = shown.remove(p.getUniqueId());
        if (s != null) {
            s.display().remove();
        }
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

    // --- model se po teleportu, smrti a odhlaseni znovu postavi ---

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        removeDisplay(p);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                sync(p);
            }
        }, 2L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        removeDisplay(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        removeDisplay(e.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeDisplay(e.getPlayer());
    }
}
