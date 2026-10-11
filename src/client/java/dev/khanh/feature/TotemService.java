package dev.khanh.feature;

import java.util.Locale;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/** Totem Styles: totems of undying use a resource-pack totem texture everywhere they appear. */
public final class TotemService {
    /**
     * Every style, listed in assets/khanh/totem_styles.txt: totems from your resource packs
     * (several animated), a totem for each Fun Skin, and Rainbow / Galaxy Shimmer.
     */
    public static final String[] STYLES = loadStyles();

    private static String[] loadStyles() {
        try (var in = TotemService.class.getResourceAsStream("/assets/khanh/totem_styles.txt")) {
            if (in != null) {
                String[] styles = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(line -> !line.isEmpty()).toArray(String[]::new);
                if (styles.length > 0) return styles;
            }
        } catch (java.io.IOException ignored) {
        }
        return new String[] {"Nebula"};
    }

    private TotemService() { }

    /** The item model to use for a totem, or null to keep vanilla's. */
    public static Identifier model(ItemStack stack) {
        if (!KhanhFeatures.enabled("totem_styles") || stack == null || !stack.isOf(Items.TOTEM_OF_UNDYING)) return null;
        String style = KhanhFeatures.choice("totem_styles", "style", STYLES[0]).toLowerCase(Locale.ROOT).replace(' ', '_');
        return Identifier.of("khanh", "totem_style_" + style);
    }
}
