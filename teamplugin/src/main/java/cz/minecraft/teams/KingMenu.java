package cz.minecraft.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Menu /king: hlavy clenu tveho tymu, kliknutim hlasujes pro krale. Hlas lze kdykoli zmenit. */
final class KingMenu implements Listener {

    private static final int MAX_SLOTS = 54;

    private final TeamsPlugin plugin;

    KingMenu(TeamsPlugin plugin) {
        this.plugin = plugin;
    }

    static final class Holder implements InventoryHolder {
        final TeamType team;
        final List<UUID> slots = new ArrayList<>();
        Inventory inventory;

        Holder(TeamType team) {
            this.team = team;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player p, TeamType team) {
        Holder holder = new Holder(team);
        int size = Math.max(9, Math.min(MAX_SLOTS, ((plugin.data.members(team).size() + 8) / 9) * 9));
        holder.inventory = Bukkit.createInventory(holder, size,
                Component.text("Král týmu " + team.display, team.color));
        fill(holder, p);
        p.openInventory(holder.inventory);
    }

    private void fill(Holder holder, Player viewer) {
        TeamType team = holder.team;
        Set<UUID> members = plugin.data.members(team);
        List<UUID> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(u -> plugin.nameOf(u).toLowerCase(Locale.ROOT)));

        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID voter : members) {
            UUID c = plugin.data.vote(voter);
            if (c != null && members.contains(c)) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        UUID myVote = plugin.data.vote(viewer.getUniqueId());
        UUID king = plugin.data.king(team);

        holder.slots.clear();
        holder.inventory.clear();
        for (int i = 0; i < sorted.size() && i < holder.inventory.getSize(); i++) {
            UUID u = sorted.get(i);
            holder.slots.add(u);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(u));
            meta.displayName(Component.text(plugin.nameOf(u), team.color, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Hlasy: " + counts.getOrDefault(u, 0), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (u.equals(king)) {
                lore.add(Component.text("👑 Současný král", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (u.equals(myVote)) {
                lore.add(Component.text("✔ Tvůj hlas", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                meta.setEnchantmentGlintOverride(true);
            } else {
                lore.add(Component.text("Klikni pro hlas", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            head.setItemMeta(meta);
            holder.inventory.setItem(i, head);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        if (plugin.teamOf(p) != holder.team) {
            p.closeInventory();
            return;
        }
        if (!plugin.kingVotingOpen()) {
            p.closeInventory();
            return;
        }
        int slot = e.getSlot();
        if (slot < 0 || slot >= holder.slots.size()) {
            return;
        }
        UUID candidate = holder.slots.get(slot);
        plugin.data.setVote(p.getUniqueId(), candidate);
        plugin.data.save();
        plugin.recomputeKing(holder.team);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        p.sendMessage(Component.text("Hlasuješ pro ", NamedTextColor.GRAY)
                .append(Component.text(plugin.nameOf(candidate), holder.team.color)));
        fill(holder, p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
