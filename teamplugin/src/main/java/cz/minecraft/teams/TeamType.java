package cz.minecraft.teams;

import net.kyori.adventure.text.format.NamedTextColor;

import java.text.Normalizer;
import java.util.Locale;

public enum TeamType {
    TRPASLICI("trpaslici", "Trpaslíci", NamedTextColor.GOLD),
    SAMURAJ("samuraj", "Samuraj", NamedTextColor.LIGHT_PURPLE),
    VODNIK("vodnik", "Vodník", NamedTextColor.AQUA),
    LIDOOP("lidoop", "Lidoop", NamedTextColor.GREEN);

    public final String id;
    public final String display;
    public final NamedTextColor color;

    TeamType(String id, String display, NamedTextColor color) {
        this.id = id;
        this.display = display;
        this.color = color;
    }

    /** Najde tym podle jmena bez ohledu na velikost pismen a diakritiku. */
    public static TeamType parse(String input) {
        if (input == null) {
            return null;
        }
        String s = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (TeamType t : values()) {
            if (t.id.equals(s)) {
                return t;
            }
        }
        return null;
    }
}
