package cz.minecraft.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vyhody tymu:
 * Trpaslici = velikost 0,75, Vodnik = delfin + conduit ve vode,
 * Lidoop = lezeni po zdech (falesna liana v packetech), Samuraj = teleport na Ctrl.
 */
final class PerkManager implements Listener {

    private static final double DWARF_SCALE = 0.75;
    private static final long PREVIEW_MAX_MS = 15_000;

    private final TeamsPlugin plugin;
    private final BlockData invisibleVine = Material.VINE.createBlockData();
    private final BlockData yellow = Material.YELLOW_CONCRETE.createBlockData();

    /** Lidoop: bloky, na kterych klient vidi falesnou lianu. */
    private final Map<UUID, Set<Block>> fakeVines = new HashMap<>();
    private final Set<UUID> flightGranted = new HashSet<>();
    private int tick;

    /** Samuraj. */
    private final Map<UUID, Boolean> sprintDown = new HashMap<>();
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    private record Preview(Set<Block> blocks, long startedAt) {
    }

    PerkManager(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::fastTick, 1L, 1L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::slowTick, 10L, 10L);
    }

    void shutdown() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            clearFake(p);
            clearPreview(p);
            AttributeInstance scale = p.getAttribute(Attribute.SCALE);
            if (scale != null && scale.getBaseValue() == DWARF_SCALE) {
                scale.setBaseValue(1.0);
            }
        }
    }

    private static boolean survival(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    // --- pomalu smycka: velikost, voda, let, preview ---

    private void slowTick() {
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            TeamType t = plugin.teamOf(p);

            // Trpaslici: velikost
            AttributeInstance scale = p.getAttribute(Attribute.SCALE);
            if (scale != null) {
                double want = t == TeamType.TRPASLICI ? DWARF_SCALE : 1.0;
                if (scale.getBaseValue() != want) {
                    scale.setBaseValue(want);
                }
            }

            // Vodnik: efekty ve vode
            if (t == TeamType.VODNIK && p.isInWater()) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 0, true, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 60, 0, true, false, false));
            }

            // Lidoop: povolene "letani", aby server nevyhodil hrace lezouciho po stene
            if (t == TeamType.LIDOOP) {
                if (survival(p) && !p.getAllowFlight()) {
                    p.setAllowFlight(true);
                    flightGranted.add(p.getUniqueId());
                }
            } else if (flightGranted.remove(p.getUniqueId()) && survival(p)) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }

            // Samuraj: preview vyprsi / obnovit zluty obraz
            Preview pv = previews.get(p.getUniqueId());
            if (pv != null) {
                if (t != TeamType.SAMURAJ || now - pv.startedAt() > PREVIEW_MAX_MS
                        || !pv.blocks().iterator().next().getWorld().equals(p.getWorld())) {
                    clearPreview(p);
                } else {
                    for (Block b : pv.blocks()) {
                        p.sendBlockChange(b.getLocation(), yellow);
                    }
                }
            }
            if (t != TeamType.LIDOOP && fakeVines.containsKey(p.getUniqueId())) {
                clearFake(p);
            }
        }
    }

    // --- Lidoop: lezeni po zdech ---

    private void fastTick() {
        tick++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.teamOf(p) == TeamType.LIDOOP && survival(p) && !p.isDead()) {
                climb(p);
            }
        }
    }

    private boolean wallAdjacent(Player p) {
        Location l = p.getLocation();
        double r = 0.45;
        double[][] dirs = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
        for (double[] d : dirs) {
            for (double h : new double[]{0.4, 1.4}) {
                Block b = l.getWorld().getBlockAt((int) Math.floor(l.getX() + d[0]),
                        (int) Math.floor(l.getY() + h), (int) Math.floor(l.getZ() + d[1]));
                if (b.getType().isSolid() && !b.isPassable()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void climb(Player p) {
        if (!wallAdjacent(p)) {
            clearFake(p);
            return;
        }
        Block base = p.getLocation().getBlock();
        Set<Block> want = new LinkedHashSet<>();
        for (int dy = -1; dy <= 2; dy++) {
            Block b = base.getRelative(0, dy, 0);
            if (b.getType().isAir()) {
                want.add(b);
            }
        }
        if (want.isEmpty()) {
            clearFake(p);
            return;
        }
        Set<Block> old = fakeVines.getOrDefault(p.getUniqueId(), Set.of());
        for (Block b : old) {
            if (!want.contains(b)) {
                p.sendBlockChange(b.getLocation(), b.getBlockData());
            }
        }
        boolean resendAll = tick % 10 == 0;
        for (Block b : want) {
            if (resendAll || !old.contains(b)) {
                p.sendBlockChange(b.getLocation(), invisibleVine);
            }
        }
        fakeVines.put(p.getUniqueId(), want);
        p.setFallDistance(0f);
    }

    private void clearFake(Player p) {
        Set<Block> old = fakeVines.remove(p.getUniqueId());
        if (old == null) {
            return;
        }
        for (Block b : old) {
            p.sendBlockChange(b.getLocation(), b.getBlockData());
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (flightGranted.contains(p.getUniqueId()) && survival(p)) {
            e.setCancelled(true);
            p.setFlying(false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.teamOf(p) != TeamType.LIDOOP && survival(p) && !p.isOp() && p.getAllowFlight()) {
            p.setAllowFlight(false);
        }
    }

    // --- Samuraj: Ctrl = nahled, druhy Ctrl = teleport ---

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Player p = e.getPlayer();
        Input in = e.getInput();
        boolean was = sprintDown.getOrDefault(p.getUniqueId(), false);
        sprintDown.put(p.getUniqueId(), in.isSprint());
        if (!in.isSprint() || was) {
            return;
        }
        if (in.isForward() || in.isBackward() || in.isLeft() || in.isRight()) {
            return;
        }
        if (plugin.teamOf(p) != TeamType.SAMURAJ || plugin.frozen(p)) {
            return;
        }
        ctrlPressed(p);
    }

    private void ctrlPressed(Player p) {
        Preview pv = previews.get(p.getUniqueId());
        if (pv == null) {
            long left = cooldownUntil.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
            if (left > 0) {
                p.sendActionBar(Component.text("Teleport se nabíjí: " + (left / 1000 + 1) + " s",
                        NamedTextColor.RED));
                return;
            }
            startPreview(p);
            return;
        }
        RayTraceResult r = p.rayTraceBlocks(radius() + 8.0, FluidCollisionMode.NEVER);
        Block hit = r == null ? null : r.getHitBlock();
        if (hit == null || !pv.blocks().contains(hit)) {
            p.sendActionBar(Component.text("Dívej se na žlutý blok a stiskni Ctrl znovu.", NamedTextColor.YELLOW));
            return;
        }
        Location target = hit.getLocation().add(0.5, 1.0, 0.5);
        target.setYaw(p.getLocation().getYaw());
        target.setPitch(p.getLocation().getPitch());
        clearPreview(p);
        p.teleport(target);
        p.setFallDistance(0f);
        p.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        int cd = plugin.getConfig().getInt("samurai.cooldown-seconds", 60);
        cooldownUntil.put(p.getUniqueId(), System.currentTimeMillis() + cd * 1000L);
    }

    private int radius() {
        return plugin.getConfig().getInt("samurai.radius", 3);
    }

    private void startPreview(Player p) {
        int r = radius();
        Block base = p.getLocation().getBlock();
        Set<Block> blocks = new LinkedHashSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block b = base.getRelative(dx, dy, dz);
                    if (!b.getType().isSolid() || b.isPassable()) {
                        continue;
                    }
                    Block up1 = b.getRelative(0, 1, 0);
                    Block up2 = b.getRelative(0, 2, 0);
                    if (up1.isPassable() && !up1.isLiquid() && up2.isPassable() && !up2.isLiquid()) {
                        blocks.add(b);
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            p.sendActionBar(Component.text("Kolem není žádné místo, kam se dá teleportovat.", NamedTextColor.RED));
            return;
        }
        previews.put(p.getUniqueId(), new Preview(blocks, System.currentTimeMillis()));
        for (Block b : blocks) {
            p.sendBlockChange(b.getLocation(), yellow);
        }
        p.sendActionBar(Component.text("Dívej se na žlutý blok a stiskni Ctrl znovu. (Shift = zrušit)",
                NamedTextColor.YELLOW));
    }

    private void clearPreview(Player p) {
        Preview pv = previews.remove(p.getUniqueId());
        if (pv == null) {
            return;
        }
        for (Block b : pv.blocks()) {
            p.sendBlockChange(b.getLocation(), b.getBlockData());
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) {
            clearPreview(e.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        clearPreview(p);
        clearFake(p);
        sprintDown.remove(p.getUniqueId());
        flightGranted.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        clearPreview(e.getEntity());
        clearFake(e.getEntity());
    }
}
