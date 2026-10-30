package cn.mythicland.dreamlib.text;

import cn.mythicland.dreamlib.integration.PlaceholderApiBridge;
import cn.mythicland.dreamlib.integration.PlaceholderService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Renders literal native placeholders, optional PlaceholderAPI placeholders, and legacy colors.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER_API_TOKEN = Pattern.compile("%[A-Za-z0-9_:-]+%");

    private final PlaceholderService placeholderService;

    /**
     * Creates a renderer and detects PlaceholderAPI without making it a compile-time dependency.
     *
     * @param owner plugin whose server and logger are used for optional integration detection
     */
    public TemplateRenderer(JavaPlugin owner) {
        this(Objects.requireNonNull(owner, "owner"), new PlaceholderApiBridge(owner));
    }

    /**
     * Creates a renderer using a shared Lib placeholder bridge.
     *
     * @param owner              plugin receiving validation context
     * @param placeholderService shared placeholder service
     */
    public TemplateRenderer(JavaPlugin owner, PlaceholderService placeholderService) {
        Objects.requireNonNull(owner, "owner");
        this.placeholderService = Objects.requireNonNull(placeholderService, "placeholderService");
    }

    /**
     * Returns whether text contains a conventional PlaceholderAPI token.
     *
     * @param text text to inspect
     * @return true when a percent-delimited token is present
     */
    public static boolean containsPlaceholderApiToken(String text) {
        return PLACEHOLDER_API_TOKEN.matcher(Objects.requireNonNull(text, "text")).find();
    }

    private static String replaceNative(String template, Map<String, ?> nativeValues) {
        String rendered = template;
        for (Map.Entry<String, ?> entry : nativeValues.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            String value = Objects.toString(entry.getValue(), "");
            rendered = rendered.replace(token, value);
        }
        return rendered;
    }

    /**
     * Renders a template for one player.
     *
     * @param template     template containing {@code {name}}-style native placeholders
     * @param player       player used by optional PlaceholderAPI
     * @param nativeValues native placeholder values without braces
     * @return translated legacy text
     */
    public String render(
            String template,
            Player player,
            Map<String, ?> nativeValues
    ) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nativeValues, "nativeValues");

        String rendered = replaceNative(template, nativeValues);
        rendered = placeholderService.render(player, rendered);
        return LegacyText.colorize(rendered);
    }

    /**
     * Returns whether PlaceholderAPI was detected for this renderer.
     *
     * @return true when PlaceholderAPI rendering is available
     */
    public boolean isPlaceholderApiAvailable() {
        return placeholderService.isAvailable();
    }

}
