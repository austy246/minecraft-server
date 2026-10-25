package cz.minecraft.teams;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Koruna je plovouci model nad helmou hrace (ItemDisplay, kazdy tick prenaseny na hlavu).
 * Majitel ji z prvni osoby nevidi.
 * Zadny predmet v inventari ani ve slotu helmy neexistuje, takze hrac o helmu neprijde.
 * Kazdy drahokam dava +1 Strength (pricita se k Strength z lektvaru).
 * Drahokam kazdeho tymu existuje ve hre jen jednou a koruna se pri zabiti presouva i s drahokamy.
 */
final class CrownManager implements Listener {

    private static final double STRENGTH_PER_GEM = 3.0;
    private final TeamsPlugin plugin;
    /** Klic stare verze, kdy byla koruna predmet. Ukliziji se zbytky. */
    private final NamespacedKey legacyCrownKey;
    private final NamespacedKey strengthKey;
    private final NamespacedKey gemKey;
    private final Map<UUID, Shown> shown = new HashMap<>();
    /** Hraci, kteri maji nacteny resource pack (ostatni by videli chybejici model). */
    private final Set<UUID> packLoaded = new HashSet<>();

    private record Shown(ItemDisplay display, String signature) {
    }

    CrownManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.legacyCrownKey = new NamespacedKey(plugin, "crown");
        this.strengthKey = new NamespacedKey(plugin, "crown_strength");
        this.gemKey = new NamespacedKey(plugin, "gem");
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
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

    /** Vzhled z resource packu pres custom_model_data; bez packu zustane vanilla predmet. */
    private void setModel(ItemMeta m, String name) {
        if (!plugin.getConfig().getBoolean("crown.use-item-model", true)) {
            return;
        }
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of(name));
        m.setCustomModelDataComponent(c);
    }

    /** Predmet, ktery se zobrazuje na hlave (model v resource packu). */
    private ItemStack displayItem(List<TeamType> gems) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Koruna", NamedTextColor.GOLD));
        setModel(m, gems.isEmpty() ? "crown_ring_empty" : "crown_ring_" + gems.get(gems.size() - 1).id);
        it.setItemMeta(m);
        return it;
    }

    // --- drzitele a drahokamy ---

    /** Zvoleny kral dostane korunu a drahokam sveho tymu, pokud ho nema nikdo jiny. */
    void grantKingCrown(UUID king, TeamType team) {
        List<TeamType> list = plugin.data.gemsOrCreate(king);
        if (plugin.data.gemHolder(team) == null && plugin.data.looseToken(team) == null) {
            list.add(team);
        }
        plugin.data.save();
        Player p = Bukkit.getPlayer(king);
        if (p != null) {
            sync(p);
        }
    }

    /**
     * Obet s jednim drahokamem prijde o korunu: zabijak ji vezme i s drahokamem.
     * Obeti s vice drahokamy vypadne jen nejnovejsi drahokam (predmet), koruna ji zustane.
     * Koruna se nikdy nekopiruje.
     */
    void transferCrown(Player killer, Player victim) {
        List<TeamType> vg = plugin.data.gems(victim.getUniqueId());
        if (vg == null || vg.isEmpty()) {
            return;
        }
        if (vg.size() >= 2) {
            dropNewestGem(killer, victim, vg);
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

    /** Kral byl prehlasovan: koruna i s drahokamy prejde na noveho krale. */
    void moveCrown(UUID from, UUID to) {
        List<TeamType> taken = plugin.data.removeCrown(from);
        plugin.data.gemsOrCreate(to).addAll(taken == null ? List.of() : taken);
        plugin.data.save();
        Player a = Bukkit.getPlayer(from);
        Player b = Bukkit.getPlayer(to);
        if (a != null) {
            sync(a);
        }
        if (b != null) {
            sync(b);
        }
    }

    // --- drahokam jako predmet ---

    private ItemStack gemItem(TeamType t, UUID token) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Drahokam: " + t.display, t.color, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("+1 Strength na koruně", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Pravý klik: nasadí se na tvoji korunu", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(gemKey, PersistentDataType.STRING, t.id + ":" + token);
        setModel(m, "gem_" + t.id);
        it.setItemMeta(m);
        return it;
    }

    private boolean isGemItem(ItemStack it) {
        return it != null && it.getType() == Material.ECHO_SHARD && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(gemKey, PersistentDataType.STRING);
    }

    /** Tym drahokamu z predmetu, nebo null, kdyz neni drahokam nebo uz neplati (po resetu). */
    private TeamType validGemTeam(ItemStack it) {
        if (!isGemItem(it)) {
            return null;
        }
        String v = it.getItemMeta().getPersistentDataContainer().get(gemKey, PersistentDataType.STRING);
        String[] parts = v.split(":");
        TeamType t = TeamType.parse(parts[0]);
        if (t == null || parts.length < 2) {
            return null;
        }
        UUID token = plugin.data.looseToken(t);
        return token != null && token.toString().equals(parts[1]) ? t : null;
    }

    /** Prikaz /drahokam [tym]: sundej drahokam z koruny do inventare. */
    void detachGem(Player p, String arg) {
        List<TeamType> gems = plugin.data.gems(p.getUniqueId());
        if (gems == null || gems.isEmpty()) {
            p.sendMessage(Component.text("Nemáš na koruně žádný drahokam.", NamedTextColor.RED));
            return;
        }
        TeamType t = arg == null ? gems.get(gems.size() - 1) : TeamType.parse(arg);
        if (t == null || !gems.contains(t)) {
            p.sendMessage(Component.text("Takový drahokam na koruně nemáš.", NamedTextColor.RED));
            return;
        }
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(Component.text("Nemáš místo v inventáři.", NamedTextColor.RED));
            return;
        }
        gems.remove(t);
        UUID token = UUID.randomUUID();
        plugin.data.setLoose(t, token);
        plugin.data.save();
        p.getInventory().addItem(gemItem(t, token));
        sync(p);
        p.sendMessage(Component.text("Sundal sis drahokam ", NamedTextColor.GRAY)
                .append(Component.text("◆ " + t.display, t.color))
                .append(Component.text(". Můžeš ho někomu dát, pravým klikem se nasadí na korunu.",
                        NamedTextColor.GRAY)));
    }

    private void dropNewestGem(Player killer, Player victim, List<TeamType> vg) {
        TeamType gem = vg.remove(vg.size() - 1);
        UUID token = UUID.randomUUID();
        plugin.data.setLoose(gem, token);
        plugin.data.save();
        Item drop = victim.getWorld().dropItemNaturally(victim.getLocation(), gemItem(gem, token));
        drop.setUnlimitedLifetime(true);
        drop.setGlowing(true);
        sync(victim);
        plugin.getServer().broadcast(Component.text(killer.getName(), NamedTextColor.GOLD)
                .append(Component.text(" zabil ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), NamedTextColor.GOLD))
                .append(Component.text(", z něhož vypadl drahokam ", NamedTextColor.GRAY))
                .append(Component.text("◆ " + gem.display, gem.color))
                .append(Component.text("!", NamedTextColor.GRAY)));
    }

    @EventHandler
    public void onUseGem(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack it = e.getItem();
        if (!isGemItem(it)) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        TeamType t = validGemTeam(it);
        if (t == null) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(Component.text("Tento drahokam už neplatí.", NamedTextColor.RED));
            return;
        }
        if (plugin.frozen(p)) {
            return;
        }
        plugin.data.gemsOrCreate(p.getUniqueId()).add(t);
        plugin.data.clearLoose(t);
        plugin.data.save();
        p.getInventory().setItemInMainHand(null);
        sync(p);
        p.sendMessage(Component.text("Nasadil sis na korunu drahokam ", NamedTextColor.GRAY)
                .append(Component.text("◆ " + t.display, t.color)).append(Component.text(".", NamedTextColor.GRAY)));
    }

    @EventHandler
    public void onGemDespawn(ItemDespawnEvent e) {
        if (isGemItem(e.getEntity().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onGemDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Item it && isGemItem(it.getItemStack())
                && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
        }
    }

    /** Po resetu mesice zbyle predmety uz neplati. */
    private void removeInvalidGems(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isGemItem(it) && validGemTeam(it) == null) {
                inv.setItem(i, null);
            }
        }
    }

    void sweep() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            sync(p);
        }
        updateVisibility();
    }

    /** Korunu vidi jen ostatni hraci s nactenym packem. Majitel ji z prvni osoby nevidi. */
    private void updateVisibility() {
        for (Map.Entry<UUID, Shown> en : shown.entrySet()) {
            ItemDisplay d = en.getValue().display();
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                boolean want = !viewer.getUniqueId().equals(en.getKey()) && packLoaded.contains(viewer.getUniqueId());
                if (viewer.canSee(d) != want) {
                    if (want) {
                        viewer.showEntity(plugin, d);
                    } else {
                        viewer.hideEntity(plugin, d);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            packLoaded.add(e.getPlayer().getUniqueId());
        } else if (e.getStatus() != PlayerResourcePackStatusEvent.Status.ACCEPTED
                && e.getStatus() != PlayerResourcePackStatusEvent.Status.DOWNLOADED) {
            packLoaded.remove(e.getPlayer().getUniqueId());
        }
        updateVisibility();
    }

    /** Zobrazi korunu drziteli, ostatnim ji odebere. Zbytky stare verze (predmet) uklidi. */
    void sync(Player p) {
        removeLegacyCrowns(p);
        removeInvalidGems(p);
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

    private float scaleOf(Player p) {
        AttributeInstance scaleAttr = p.getAttribute(Attribute.SCALE);
        return scaleAttr == null ? 1f : (float) scaleAttr.getValue();
    }

    private Transformation transformation(Player p) {
        float s = scaleOf(p);
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    /** Stred koruny: spodek prstence lezi tesne pod temenem hlavy, ve smeru pohledu hrace. */
    private Location headLocation(Player p) {
        Location l = p.getLocation();
        l.setY(l.getY() + p.getBoundingBox().getHeight() + 0.515 * scaleOf(p));
        l.setPitch(0f);
        return l;
    }

    private void ensureDisplay(Player p, List<TeamType> gems) {
        UUID u = p.getUniqueId();
        String sig = signature(gems);
        if (p.isInvisible()) {
            removeDisplay(p);
            return;
        }
        Shown s = shown.get(u);
        if (s != null && (!s.display().isValid() || !s.display().getWorld().equals(p.getWorld()))) {
            s.display().remove();
            shown.remove(u);
            s = null;
        }
        if (s == null) {
            ItemDisplay d = p.getWorld().spawn(headLocation(p), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setItemStack(displayItem(gems));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setTransformation(transformation(p));
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTeleportDuration(1);
            });
            shown.put(u, new Shown(d, sig));
            updateVisibility();
            return;
        }
        if (!sig.equals(s.signature())) {
            s.display().setItemStack(displayItem(gems));
            shown.put(u, new Shown(s.display(), sig));
        }
        s.display().setTransformation(transformation(p));
    }

    /** Kazdy tick prenese koruny na hlavy jejich majitelu. */
    private void follow() {
        for (Map.Entry<UUID, Shown> en : shown.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            ItemDisplay d = en.getValue().display();
            if (p != null && d.isValid() && p.getWorld().equals(d.getWorld())) {
                d.teleport(headLocation(p));
            }
        }
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
        packLoaded.remove(e.getPlayer().getUniqueId());
        removeDisplay(e.getPlayer());
    }
}
