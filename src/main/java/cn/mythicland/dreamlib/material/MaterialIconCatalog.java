package cn.mythicland.dreamlib.material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolves legacy Minecraft material entries to small browser-friendly icon URLs.
 *
 * <p>The URL points at the versioned 1.12.2 asset mirror. Consumers must always
 * provide a visual fallback because custom or plugin-provided materials do not
 * have a vanilla texture.</p>
 */
public final class MaterialIconCatalog {

    private static final String BASE =
            "https://assets.mcasset.cloud/1.12.2/assets/minecraft/textures/";
    private static final String[] WOOL_COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    /**
     * Returns candidate URLs in best-first order.
     *
     * @param materialName Bukkit material name
     * @param data         legacy data value
     * @return immutable candidate URL list
     */
    public List<String> iconUrls(String materialName, int data) {
        if (materialName == null || materialName.isBlank()) return List.of();
        String name = materialName.trim().toUpperCase(Locale.ROOT);
        String lower = name.toLowerCase(Locale.ROOT);
        List<String> urls = new ArrayList<>();
        String special = specialTexture(name, data);
        if (special != null) urls.add(BASE + special);

        if (isItemTexture(name)) urls.add(BASE + "items/" + lower + ".png");
        urls.add(BASE + "blocks/" + lower + ".png");
        if (!isItemTexture(name)) urls.add(BASE + "items/" + lower + ".png");
        return List.copyOf(urls);
    }

    /**
     * Returns the first candidate URL, or an empty string when no material was supplied.
     */
    public String iconUrl(String materialName, int data) {
        return iconUrls(materialName, data).stream().findFirst().orElse("");
    }

    private String specialTexture(String name, int data) {
        if (name.equals("WOOL") && data >= 0 && data < WOOL_COLORS.length) {
            return "blocks/wool_colored_" + WOOL_COLORS[data] + ".png";
        }
        return switch (name) {
            case "INK_SACK", "DYE" -> "items/dye_powder.png";
            case "COCOA_BEANS" -> "items/dye_powder_brown.png";
            case "LAPIS_LAZULI" -> "items/dye_powder_blue.png";
            case "ROSE_RED" -> "items/dye_powder_red.png";
            case "CACTUS_GREEN" -> "items/dye_powder_green.png";
            case "DANDELION_YELLOW" -> "items/dye_powder_yellow.png";
            default -> null;
        };
    }

    private boolean isItemTexture(String name) {
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.endsWith("_GEM")
                || name.endsWith("_DUST") || name.endsWith("_BUCKET") || name.endsWith("_SPAWN_EGG")
                || List.of("APPLE", "BREAD", "CARROT", "POTATO", "STICK", "BOW", "ARROW", "BOOK",
                        "PAPER", "SHEARS", "FLINT", "COAL", "DIAMOND", "EMERALD", "GOLDEN_APPLE",
                        "FISHING_ROD", "LEATHER", "STRING", "FEATHER", "EGG", "SNOWBALL", "SLIME_BALL")
                .contains(name);
    }
}
