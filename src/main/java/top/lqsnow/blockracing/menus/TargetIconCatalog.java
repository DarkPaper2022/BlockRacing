package top.lqsnow.blockracing.menus;

import org.bukkit.Material;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Presentation metadata only: this catalog never participates in goal completion/scoring. */
public final class TargetIconCatalog {
    private static final Map<String, Icon> ICONS = load();

    private TargetIconCatalog() { }

    public static Icon matching(String target, String requirement) {
        if (target == null) return null;
        String id = target.startsWith("DRAFTOUT:") ? target.substring("DRAFTOUT:".length()) : target;
        Icon icon = ICONS.get(id);
        // Server-edited requirements must not retain a misleading bundled picture or quantity.
        return icon != null && icon.requirement().equals(requirement) ? icon : null;
    }

    public static Map<String, Icon> all() {
        return ICONS;
    }

    private static Map<String, Icon> load() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                TargetIconCatalog.class.getResourceAsStream("/target-icons.json")), StandardCharsets.UTF_8)) {
            JSONObject data = (JSONObject) new JSONParser().parse(reader);
            Map<String, Icon> icons = new HashMap<>();
            for (Object key : data.keySet()) {
                String id = (String) key;
                JSONObject value = (JSONObject) data.get(key);
                icons.put(id, new Icon(id, (String) value.get("requirement"),
                        Material.valueOf((String) value.get("icon")), (String) value.get("action"),
                        (String) value.get("badge"), ((Number) value.get("count")).intValue(),
                        Boolean.TRUE.equals(value.get("glint"))));
            }
            return Map.copyOf(icons);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid bundled target icon catalog", ex);
        }
    }

    public record Icon(String id, String requirement, Material material, String action,
                       String badge, int count, boolean glint) {
        public String modelKey() {
            return "blockracing:task/" + id.toLowerCase(Locale.ROOT);
        }

        public String actionLabel(boolean chinese) {
            return switch (action) {
                case "collect" -> chinese ? "收集" : "Collect";
                case "mine" -> chinese ? "挖掘" : "Mine";
                case "kill" -> chinese ? "击杀" : "Kill";
                case "breed" -> chinese ? "繁殖" : "Breed";
                case "tame" -> chinese ? "驯服" : "Tame";
                case "wear" -> chinese ? "穿戴" : "Wear";
                case "advance" -> chinese ? "进度" : "Advancement";
                case "eat" -> chinese ? "食用" : "Consume";
                case "craft" -> chinese ? "合成" : "Craft";
                case "use" -> chinese ? "使用" : "Use";
                case "effect" -> chinese ? "效果" : "Effect";
                case "death" -> chinese ? "死亡" : "Die";
                case "level" -> chinese ? "等级" : "Level";
                case "travel" -> chinese ? "到达" : "Reach";
                case "fish" -> chinese ? "钓鱼" : "Fish";
                case "spy" -> chinese ? "观察" : "Observe";
                case "damage" -> chinese ? "伤害" : "Damage";
                case "trade" -> chinese ? "交易" : "Trade";
                case "hunger" -> chinese ? "饥饿" : "Hunger";
                case "enchant" -> chinese ? "附魔" : "Enchant";
                default -> chinese ? "目标" : "Goal";
            };
        }

        /** Never silently turn a 100/200/400 requirement into a capped stack count. */
        public int stackAmount() {
            return count >= 2 && count <= 64 && badge.equals(Integer.toString(count)) ? count : 1;
        }
    }
}
