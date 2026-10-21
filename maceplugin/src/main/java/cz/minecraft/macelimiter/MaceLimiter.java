package cz.minecraft.macelimiter;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

/**
 * Mace: zadne enchanty (stavajici se smazou, nove nejdou dat)
 * a 60 sekund nabijeni po kazdem uderu.
 */
public final class MaceLimiter extends JavaPlugin implements Listener {

    private static final int COOLDOWN_TICKS = 60 * 20;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        // Pojistka: kazde 2 s ocisti inventar a enderchest vsech hracu
        // (zachyti i itemy z lootu, dropu, commandu...).
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                sanitize(p.getInventory());
                sanitize(p.getEnderChest());
            }
        }, 40L, 40L);
    }

    private static boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }

    private static void strip(ItemStack item) {
        if (!isMace(item) || item.getEnchantments().isEmpty()) {
            return;
        }
        for (Enchantment e : new ArrayList<>(item.getEnchantments().keySet())) {
            item.removeEnchantment(e);
        }
    }

    private static void sanitize(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            strip(item);
        }
    }

    // --- nabijeni ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isMace(hand)) {
            return;
        }
        if (player.hasCooldown(Material.MACE)) {
            event.setCancelled(true);
            return;
        }
        player.setCooldown(Material.MACE, COOLDOWN_TICKS);
    }

    // --- zadne enchanty na mace ---

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack result = event.getResult();
        if (isMace(first) && result != null && !result.getEnchantments().isEmpty()) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (isMace(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        if (isMace(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent event) {
        // Grindstone enchanty odebira, mace tedy proste vyjde cista.
        ItemStack result = event.getResult();
        strip(result);
        event.setResult(result);
    }

    // --- odstraneni stavajicich ---

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sanitize(event.getPlayer().getInventory());
        sanitize(event.getPlayer().getEnderChest());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        sanitize(event.getInventory());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        strip(event.getCurrentItem());
        strip(event.getCursor());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        strip(stack);
        event.getItem().setItemStack(stack);
    }
}
