package top.lqsnow.blockracing.utils;

import org.bukkit.Material;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import top.lqsnow.blockracing.Main;
import top.lqsnow.blockracing.managers.Message;

import java.io.File;
import java.io.FileReader;
import java.util.Objects;
import java.util.logging.Level;

public class TranslationUtil {
    private static String cachedLang;
    private static JSONObject cachedTranslations;

    public static String getValue(String block) {
        try {
            String key = Objects.requireNonNull(Material.getMaterial(block)).getTranslationKey();
            if (block.equalsIgnoreCase("NETHER_WART")) {
                key = "block.minecraft.nether_wart";
            }
            return (String) getTranslations().get(key);
        } catch (Exception e) {
            Main.getInstance().getLogger().log(Level.SEVERE, "[BlockRacing] Error getting value of blocks!", e);
        }
        return null;
    }

    private static JSONObject getTranslations() throws Exception {
        String lang = Message.MESSAGE_LANG.getString();
        if (cachedTranslations != null && lang.equals(cachedLang)) {
            return cachedTranslations;
        }

        File file = new File(Main.getInstance().getDataFolder(), lang + ".json");
        try (FileReader reader = new FileReader(file)) {
            JSONParser parser = new JSONParser();
            cachedTranslations = (JSONObject) parser.parse(reader);
            cachedLang = lang;
            return cachedTranslations;
        }
    }
}
