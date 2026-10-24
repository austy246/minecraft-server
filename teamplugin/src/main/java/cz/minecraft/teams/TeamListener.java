package cz.minecraft.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Stejny tym se nezrani, hrac bez tymu je zablokovany, 3 zabiti = prebehnuti, koruny pri zabiti krale. */
final class TeamListener implements Listener {

    private final TeamsPlugin plugin;

    TeamListener(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.playerReady(e.getPlayer());
    }

    // --- hrac bez tymu nemuze hrat ---

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.frozen(e.getPlayer())) {
            return;
        }
        if (e.getTo().getX() != e.getFrom().getX() || e.getTo().getZ() != e.getFrom().getZ()
                || e.getTo().getY() > e.getFrom().getY()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (plugin.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (plugin.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent e) {
        if (plugin.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && plugin.frozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && plugin.frozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.frozen(p) || p.isOp()) {
            return;
        }
        String cmd = e.getMessage().split(" ")[0].toLowerCase();
        if (!cmd.equals("/join")) {
            e.setCancelled(true);
            p.sendMessage(Component.text("Nejdřív si vyber tým: /join team", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.frozen(p)) {
            e.setCancelled(true);
        }
    }

    // --- stejny tym se nezrani ---

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            return p;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null) {
            return;
        }
        if (plugin.frozen(attacker)) {
            e.setCancelled(true);
            return;
        }
        if (attacker != victim && plugin.sameTeam(attacker, victim)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(e.getCombuster());
        if (attacker != null && attacker != victim && plugin.sameTeam(attacker, victim)) {
            e.setCancelled(true);
        }
    }

    private static boolean harmful(Collection<PotionEffect> effects) {
        for (PotionEffect pe : effects) {
            if (pe.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        ProjectileSource src = e.getEntity().getShooter();
        if (!(src instanceof Player thrower) || !harmful(e.getPotion().getEffects())) {
            return;
        }
        for (LivingEntity le : new ArrayList<>(e.getAffectedEntities())) {
            if (le instanceof Player p && p != thrower && plugin.sameTeam(thrower, p)) {
                e.setIntensity(le, 0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud cloud = e.getEntity();
        if (!(cloud.getSource() instanceof Player thrower)) {
            return;
        }
        List<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        PotionType base = cloud.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        if (!harmful(effects)) {
            return;
        }
        e.getAffectedEntities().removeIf(le ->
                le instanceof Player p && p != thrower && plugin.sameTeam(thrower, p));
    }

    // --- zabiti ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer == victim) {
            return;
        }
        TeamType killerTeam = plugin.teamOf(killer);
        TeamType victimTeam = plugin.teamOf(victim);
        if (killerTeam == null || victimTeam == null || killerTeam == victimTeam) {
            return;
        }

        // koruna se presouva i s drahokamy
        plugin.crowns.transferCrown(killer, victim);

        // 3 zabiti stejnym tymem = obet prebehne
        int count = plugin.data.addKill(victim.getUniqueId(), killerTeam);
        if (count >= TeamsPlugin.KILLS_TO_CONVERT) {
            plugin.assign(victim.getUniqueId(), killerTeam);
            plugin.getServer().broadcast(Component.text(victim.getName(), victimTeam.color)
                    .append(Component.text(" byl zabit 3× týmem ", NamedTextColor.GRAY))
                    .append(plugin.teamName(killerTeam))
                    .append(Component.text(" a přešel do něj.", NamedTextColor.GRAY)));
        } else {
            plugin.data.save();
            victim.sendMessage(Component.text("Tým ", NamedTextColor.GRAY)
                    .append(plugin.teamName(killerTeam))
                    .append(Component.text(" tě zabil " + count + "/" + TeamsPlugin.KILLS_TO_CONVERT
                            + "×. Při " + TeamsPlugin.KILLS_TO_CONVERT + " zabitích přejdeš do něj.",
                            NamedTextColor.GRAY)));
        }
    }
}
