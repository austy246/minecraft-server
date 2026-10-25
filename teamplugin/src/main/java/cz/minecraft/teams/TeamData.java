package cz.minecraft.teams;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Ulozena data: tymy, pocty zabiti, hlasy, kralove a drahokamy. Drzi se v pameti a uklada do data.yml. */
final class TeamData {

    private final File file;
    private String period;
    private int migration;
    private final Map<UUID, TeamType> teams = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    /** obet -> (tym zabijaku -> pocet) */
    private final Map<UUID, Map<TeamType, Integer>> kills = new HashMap<>();
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Map<TeamType, UUID> kings = new EnumMap<>(TeamType.class);
    /** drzitel koruny -> drahokamy v poradi ziskani (posledni = nejnovejsi) */
    private final Map<UUID, List<TeamType>> gems = new HashMap<>();
    /** drahokamy, ktere existuji jako predmet (tym -> token platneho predmetu) */
    private final Map<TeamType, UUID> loose = new EnumMap<>(TeamType.class);

    TeamData(File file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        period = y.getString("period");
        migration = y.getInt("migration", 0);
        ConfigurationSection s = y.getConfigurationSection("teams");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                TeamType t = TeamType.parse(s.getString(k));
                if (t != null) {
                    teams.put(UUID.fromString(k), t);
                }
            }
        }
        s = y.getConfigurationSection("names");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                names.put(UUID.fromString(k), s.getString(k));
            }
        }
        s = y.getConfigurationSection("kills");
        if (s != null) {
            for (String victim : s.getKeys(false)) {
                ConfigurationSection v = s.getConfigurationSection(victim);
                if (v == null) {
                    continue;
                }
                Map<TeamType, Integer> m = new EnumMap<>(TeamType.class);
                for (String tk : v.getKeys(false)) {
                    TeamType t = TeamType.parse(tk);
                    if (t != null) {
                        m.put(t, v.getInt(tk));
                    }
                }
                kills.put(UUID.fromString(victim), m);
            }
        }
        s = y.getConfigurationSection("votes");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                votes.put(UUID.fromString(k), UUID.fromString(s.getString(k)));
            }
        }
        s = y.getConfigurationSection("kings");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                TeamType t = TeamType.parse(k);
                if (t != null) {
                    kings.put(t, UUID.fromString(s.getString(k)));
                }
            }
        }
        s = y.getConfigurationSection("loose");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                TeamType t = TeamType.parse(k);
                if (t != null) {
                    loose.put(t, UUID.fromString(s.getString(k)));
                }
            }
        }
        s = y.getConfigurationSection("gems");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                List<TeamType> list = new ArrayList<>();
                for (String id : s.getStringList(k)) {
                    TeamType t = TeamType.parse(id);
                    if (t != null) {
                        list.add(t);
                    }
                }
                gems.put(UUID.fromString(k), list);
            }
        }
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("period", period);
        y.set("migration", migration);
        teams.forEach((u, t) -> y.set("teams." + u, t.id));
        names.forEach((u, n) -> y.set("names." + u, n));
        kills.forEach((u, m) -> m.forEach((t, c) -> y.set("kills." + u + "." + t.id, c)));
        votes.forEach((a, b) -> y.set("votes." + a, b.toString()));
        kings.forEach((t, u) -> y.set("kings." + t.id, u.toString()));
        loose.forEach((t, u) -> y.set("loose." + t.id, u.toString()));
        gems.forEach((u, list) -> y.set("gems." + u, list.stream().map(t -> t.id).toList()));
        try {
            y.save(file);
        } catch (IOException e) {
            throw new IllegalStateException("Nelze ulozit " + file, e);
        }
    }

    int migration() {
        return migration;
    }

    void setMigration(int m) {
        migration = m;
    }

    String period() {
        return period;
    }

    void setPeriod(String period) {
        this.period = period;
    }

    TeamType teamOf(UUID u) {
        return teams.get(u);
    }

    void setTeam(UUID u, TeamType t) {
        teams.put(u, t);
    }

    Set<UUID> members(TeamType t) {
        Set<UUID> out = new LinkedHashSet<>();
        teams.forEach((u, tt) -> {
            if (tt == t) {
                out.add(u);
            }
        });
        return out;
    }

    String name(UUID u) {
        return names.get(u);
    }

    void setName(UUID u, String name) {
        names.put(u, name);
    }

    /** Pricte zabiti obeti tymem a vrati novy pocet. */
    int addKill(UUID victim, TeamType killerTeam) {
        return kills.computeIfAbsent(victim, k -> new EnumMap<>(TeamType.class))
                .merge(killerTeam, 1, Integer::sum);
    }

    void clearKills(UUID victim) {
        kills.remove(victim);
    }

    UUID vote(UUID voter) {
        return votes.get(voter);
    }

    void setVote(UUID voter, UUID candidate) {
        votes.put(voter, candidate);
    }

    void removeVote(UUID voter) {
        votes.remove(voter);
    }

    UUID king(TeamType t) {
        return kings.get(t);
    }

    void setKing(TeamType t, UUID u) {
        if (u == null) {
            kings.remove(t);
        } else {
            kings.put(t, u);
        }
    }

    // --- koruny a drahokamy ---

    /** Drahokamy drzitele koruny, nebo null, kdyz korunu nema. */
    List<TeamType> gems(UUID holder) {
        return gems.get(holder);
    }

    boolean hasCrown(UUID holder) {
        return gems.containsKey(holder);
    }

    List<TeamType> gemsOrCreate(UUID holder) {
        return gems.computeIfAbsent(holder, k -> new ArrayList<>());
    }

    /** Odebere hraci korunu i s drahokamy a vrati je. */
    List<TeamType> removeCrown(UUID holder) {
        return gems.remove(holder);
    }

    UUID looseToken(TeamType t) {
        return loose.get(t);
    }

    void setLoose(TeamType t, UUID token) {
        loose.put(t, token);
    }

    void clearLoose(TeamType t) {
        loose.remove(t);
    }

    /** Kdo drzi drahokam daneho tymu, nebo null, kdyz ho nikdo nema. */
    UUID gemHolder(TeamType t) {
        for (Map.Entry<UUID, List<TeamType>> e : gems.entrySet()) {
            if (e.getValue().contains(t)) {
                return e.getKey();
            }
        }
        return null;
    }

    Set<UUID> crownHolders() {
        return new LinkedHashSet<>(gems.keySet());
    }

    /** Mesicni reset: vsechno krome jmen. */
    void resetAll() {
        teams.clear();
        kills.clear();
        votes.clear();
        kings.clear();
        gems.clear();
        loose.clear();
    }
}
