package cz.minecraft.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mesicni tymy (Trpaslici, Samuraj, Vodnik, Lidoop), hlasovani o krali,
 * koruna s drahokamy a vyhody tymu.
 */
public final class TeamsPlugin extends JavaPlugin implements TabExecutor {

    static final ZoneId ZONE = ZoneId.of("Europe/Prague");
    static final int KILLS_TO_CONVERT = 3;
    /** Krale lze volit po 3 dnech od zacatku mesice, tedy od 4. dne. */
    static final int KING_FROM_DAY = 4;

    TeamData data;
    CrownManager crowns;
    PerkManager perks;
    KingMenu kingMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new TeamData(new File(getDataFolder(), "data.yml"));
        crowns = new CrownManager(this);
        perks = new PerkManager(this);
        kingMenu = new KingMenu(this);

        getServer().getPluginManager().registerEvents(new TeamListener(this), this);
        getServer().getPluginManager().registerEvents(kingMenu, this);
        getServer().getPluginManager().registerEvents(perks, this);
        getServer().getPluginManager().registerEvents(crowns, this);

        getCommand("join").setExecutor(this);
        getCommand("join").setTabCompleter(this);
        getCommand("king").setExecutor(this);
        getCommand("drahokam").setExecutor(this);
        getCommand("pack").setExecutor(this);
        getCommand("drahokam").setTabCompleter(this);

        checkPeriod();
        cleanCrowns(data.migration() < 1);
        data.setMigration(1);
        data.save();
        for (Player p : getServer().getOnlinePlayers()) {
            playerReady(p);
        }

        getServer().getScheduler().runTaskTimer(this, this::checkPeriod, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> cleanCrowns(false), 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, this::remindFrozen, 100L, 100L);
        perks.start();
        crowns.start();
    }

    @Override
    public void onDisable() {
        perks.shutdown();
        crowns.shutdown();
        data.save();
    }

    // --- pomocne ---

    TeamType teamOf(Player p) {
        return data.teamOf(p.getUniqueId());
    }

    boolean frozen(Player p) {
        return teamOf(p) == null;
    }

    boolean sameTeam(Player a, Player b) {
        TeamType t = teamOf(a);
        return t != null && t == teamOf(b);
    }

    boolean kingVotingOpen() {
        return LocalDate.now(ZONE).getDayOfMonth() >= KING_FROM_DAY;
    }

    String nameOf(UUID u) {
        Player online = Bukkit.getPlayer(u);
        if (online != null) {
            return online.getName();
        }
        String n = data.name(u);
        return n != null ? n : u.toString().substring(0, 8);
    }

    Component teamName(TeamType t) {
        return Component.text(t.display, t.color);
    }

    // --- start hrace a mesicni reset ---

    void playerReady(Player p) {
        data.setName(p.getUniqueId(), p.getName());
        applyScoreboard(p);
        if (frozen(p)) {
            promptTeam(p);
        }
    }

    void checkPeriod() {
        String now = YearMonth.now(ZONE).toString();
        if (now.equals(data.period())) {
            return;
        }
        boolean first = data.period() == null;
        data.resetAll();
        data.setPeriod(now);
        data.save();
        for (Player p : getServer().getOnlinePlayers()) {
            applyScoreboard(p);
            if (!first) {
                promptTeam(p);
            }
        }
        if (!first) {
            getServer().broadcast(Component.text("Nový měsíc! Týmy, koruny a hlasy byly resetovány. "
                    + "Vyber si tým příkazem /join team.", NamedTextColor.GOLD));
        }
    }

    /**
     * Prazdna koruna hrace, ktery neni kral, nema smysl a zmizi.
     * Pri jednorazove migraci (po zmene pravidla o prehlasovani) se koruny clenu, ktere
     * obsahuji jen drahokam jejich vlastniho tymu, presunou na krale tymu.
     */
    void cleanCrowns(boolean migrate) {
        boolean changed = false;
        for (UUID holder : data.crownHolders()) {
            List<TeamType> gems = data.gems(holder);
            TeamType team = data.teamOf(holder);
            UUID king = team == null ? null : data.king(team);
            if (gems == null || holder.equals(king)) {
                continue;
            }
            if (gems.isEmpty()) {
                data.removeCrown(holder);
                changed = true;
            } else if (migrate && king != null && gems.stream().allMatch(g -> g == team)) {
                crowns.moveCrown(holder, king);
                changed = true;
            }
        }
        if (changed) {
            data.save();
        }
    }

    void promptTeam(Player p) {
        p.sendMessage(Component.text("Vyber si tým, jinak nemůžeš hrát: ", NamedTextColor.GOLD)
                .append(Component.text("/join team", NamedTextColor.YELLOW)));
        showTeams(p);
    }

    private void remindFrozen() {
        for (Player p : getServer().getOnlinePlayers()) {
            if (frozen(p)) {
                p.showTitle(Title.title(
                        Component.text("Vyber si tým", NamedTextColor.GOLD),
                        Component.text("/join team <trpaslici|samuraj|vodnik|lidoop>", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(6), Duration.ZERO)));
            }
        }
    }

    // --- tymy ---

    void applyScoreboard(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        TeamType mine = teamOf(p);
        for (TeamType t : TeamType.values()) {
            Team st = sb.getTeam("tm_" + t.id);
            if (st == null) {
                st = sb.registerNewTeam("tm_" + t.id);
            }
            st.color(t.color);
            st.prefix(Component.text("[" + t.display + "] ", t.color));
            if (t == mine) {
                st.addEntry(p.getName());
            } else {
                st.removeEntry(p.getName());
            }
        }
    }

    /** Presune hrace do tymu (join nebo prebehnuti po 3 zabitich). */
    void assign(UUID uuid, TeamType newTeam) {
        TeamType old = data.teamOf(uuid);
        data.setTeam(uuid, newTeam);
        data.clearKills(uuid);
        data.removeVote(uuid);
        data.save();
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            applyScoreboard(online);
        }
        if (old != null) {
            recomputeKing(old);
        }
        recomputeKing(newTeam);
    }

    void showTeams(Player p) {
        p.sendMessage(Component.text("Týmy (klikni pro vstup):", NamedTextColor.GOLD));
        for (TeamType t : TeamType.values()) {
            List<String> names = new ArrayList<>();
            for (UUID u : data.members(t)) {
                names.add(nameOf(u));
            }
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            Component line = Component.text("• " + t.display, t.color, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/join team " + t.id))
                    .append(Component.text(" (" + names.size() + "): ", NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, false))
                    .append(Component.text(names.isEmpty() ? "nikdo" : String.join(", ", names),
                            NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
            p.sendMessage(line);
        }
    }

    // --- kral ---

    void recomputeKing(TeamType t) {
        Set<UUID> members = data.members(t);
        Map<UUID, Integer> counts = new HashMap<>();
        for (UUID voter : members) {
            UUID c = data.vote(voter);
            if (c != null && members.contains(c)) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        UUID current = data.king(t);
        if (current != null && !members.contains(current)) {
            current = null;
        }
        UUID newKing = null;
        if (!counts.isEmpty()) {
            int max = Collections.max(counts.values());
            List<UUID> top = new ArrayList<>();
            counts.forEach((u, c) -> {
                if (c == max) {
                    top.add(u);
                }
            });
            if (current != null && top.contains(current)) {
                newKing = current;
            } else {
                top.sort(Comparator.comparing(u -> nameOf(u).toLowerCase(Locale.ROOT)));
                newKing = top.get(0);
            }
        }
        if (Objects.equals(newKing, data.king(t))) {
            return;
        }
        UUID oldKing = data.king(t);
        data.setKing(t, newKing);
        if (newKing != null) {
            if (oldKing != null && !oldKing.equals(newKing) && data.hasCrown(oldKing)) {
                crowns.moveCrown(oldKing, newKing);
            } else {
                crowns.grantKingCrown(newKing, t);
            }
            Component msg = Component.text("👑 ", NamedTextColor.GOLD)
                    .append(Component.text(nameOf(newKing), t.color))
                    .append(Component.text(" je novým králem týmu ", NamedTextColor.GOLD))
                    .append(teamName(t)).append(Component.text("!", NamedTextColor.GOLD));
            getServer().broadcast(msg);
        }
        data.save();
    }

    // --- prikazy ---

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Jen pro hrace.");
            return true;
        }
        if (cmd.getName().equals("king")) {
            openKing(p);
            return true;
        }
        if (cmd.getName().equals("pack")) {
            crowns.sendPack(p);
            p.sendMessage(Component.text("Resource pack ti byl znovu nabídnut.", NamedTextColor.GRAY));
            return true;
        }
        if (cmd.getName().equals("drahokam")) {
            crowns.detachGem(p, args.length > 0 ? args[0] : null);
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("team")) {
            p.sendMessage(Component.text("Použití: /join team <trpaslici|samuraj|vodnik|lidoop>",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length == 1) {
            showTeams(p);
            return true;
        }
        TeamType already = teamOf(p);
        if (already != null) {
            p.sendMessage(Component.text("Už jsi v týmu ", NamedTextColor.RED)
                    .append(teamName(already)).append(Component.text(" do konce měsíce.", NamedTextColor.RED)));
            return true;
        }
        TeamType t = TeamType.parse(args[1]);
        if (t == null) {
            p.sendMessage(Component.text("Takový tým neexistuje. Použij: trpaslici, samuraj, vodnik, lidoop.",
                    NamedTextColor.RED));
            return true;
        }
        assign(p.getUniqueId(), t);
        getServer().broadcast(Component.text(p.getName(), t.color)
                .append(Component.text(" se přidal do týmu ", NamedTextColor.GRAY))
                .append(teamName(t)).append(Component.text(".", NamedTextColor.GRAY)));
        return true;
    }

    private void openKing(Player p) {
        TeamType t = teamOf(p);
        if (t == null) {
            p.sendMessage(Component.text("Nejdřív si vyber tým (/join team).", NamedTextColor.RED));
            return;
        }
        if (!kingVotingOpen()) {
            p.sendMessage(Component.text("Hlasování o králi je možné od " + KING_FROM_DAY
                    + ". dne v měsíci.", NamedTextColor.RED));
            return;
        }
        kingMenu.open(p, t);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equals("drahokam")) {
            if (args.length == 1 && sender instanceof Player p) {
                List<TeamType> mine = data.gems(p.getUniqueId());
                if (mine != null) {
                    mine.forEach(t -> out.add(t.id));
                }
                out.removeIf(x -> !x.startsWith(args[0].toLowerCase(Locale.ROOT)));
            }
            return out;
        }
        if (!cmd.getName().equals("join")) {
            return out;
        }
        if (args.length == 1) {
            out.add("team");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            for (TeamType t : TeamType.values()) {
                out.add(t.id);
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }
}
