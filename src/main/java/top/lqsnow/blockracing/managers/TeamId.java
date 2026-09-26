package top.lqsnow.blockracing.managers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum TeamId {
    RED("red", "§c", NamedTextColor.RED, Material.RED_WOOL, Material.RED_CONCRETE),
    BLUE("blue", "§9", NamedTextColor.BLUE, Material.BLUE_WOOL, Material.BLUE_CONCRETE),
    GREEN("green", "§a", NamedTextColor.GREEN, Material.LIME_WOOL, Material.LIME_CONCRETE),
    YELLOW("yellow", "§e", NamedTextColor.YELLOW, Material.YELLOW_WOOL, Material.YELLOW_CONCRETE);

    public static final List<TeamId> ALL = List.of(values());

    private final String id;
    private final String colorCode;
    private final NamedTextColor textColor;
    private final Material woolMaterial;
    private final Material concreteMaterial;

    TeamId(String id, String colorCode, NamedTextColor textColor, Material woolMaterial, Material concreteMaterial) {
        this.id = id;
        this.colorCode = colorCode;
        this.textColor = textColor;
        this.woolMaterial = woolMaterial;
        this.concreteMaterial = concreteMaterial;
    }

    public String id() {
        return id;
    }

    public String colorCode() {
        return colorCode;
    }

    public NamedTextColor textColor() {
        return textColor;
    }

    public Material woolMaterial() {
        return woolMaterial;
    }

    public Material concreteMaterial() {
        return concreteMaterial;
    }

    public static TeamId fromString(String name) {
        if (name == null) return null;
        for (TeamId t : values()) {
            if (t.id.equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    public static boolean isValid(String name) {
        return fromString(name) != null;
    }
}
