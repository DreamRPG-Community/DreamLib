package cn.mythicland.dreamlib.item;

import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the generic label/value shape used by legacy RPG item lore.
 */
public final class LoreAttributeParser {

    private static final Pattern LABEL_VALUE_PATTERN = Pattern.compile("^\\s*(.*?)\\s*[:：]\\s*(.*?)\\s*$");

    private LoreAttributeParser() {
    }

    /**
     * Parses all valid numeric attribute lines in a lore collection.
     *
     * @param lore the lore lines
     * @return valid parsed attribute lines
     */
    public static List<LoreAttributeLine> parse(Collection<String> lore) {
        Objects.requireNonNull(lore, "lore");
        return lore.stream()
                .map(LoreAttributeParser::parseLine)
                .filter(LoreAttributeParseResult::isValid)
                .map(result -> result.attribute().orElseThrow())
                .toList();
    }

    /**
     * Reads and parses an ItemStack's lore.
     *
     * @param itemStack the item to inspect
     * @return valid parsed attribute lines
     */
    public static List<LoreAttributeLine> parse(ItemStack itemStack) {
        return parse(ItemLoreReader.read(itemStack));
    }

    /**
     * Parses one legacy lore line.
     *
     * @param line the original lore line
     * @return a parse result including a useful error for malformed numeric lines
     */
    public static LoreAttributeParseResult parseLine(String line) {
        if (line == null || line.isBlank()) return LoreAttributeParseResult.notAttribute();

        String colored = LegacyText.colorize(line);
        if (colored.isBlank()) return LoreAttributeParseResult.notAttribute();
        Matcher matcher = LABEL_VALUE_PATTERN.matcher(colored);
        if (!matcher.matches()) return LoreAttributeParseResult.notAttribute();

        String rawLabel = matcher.group(1);
        String rawValue = matcher.group(2);
        String label = LegacyText.stripColor(rawLabel).trim();
        String valueText = LegacyText.stripColor(rawValue).trim();
        String labelColor = LegacyText.firstColorCode(rawLabel);
        if (label.isBlank()) return LoreAttributeParseResult.invalid(null, "属性名称为空");
        if (valueText.isBlank()) return LoreAttributeParseResult.invalid(label, "属性数值为空");

        try {
            NumericRange value = NumericRange.parse(valueText);
            return LoreAttributeParseResult.valid(new LoreAttributeLine(label, labelColor, value, line));
        } catch (IllegalArgumentException exception) {
            return LoreAttributeParseResult.invalid(label, exception.getMessage());
        }
    }

    /**
     * Normalizes a display label for case-insensitive alias matching.
     *
     * @param label the label to normalize
     * @return the normalized label
     */
    public static String normalizeLabel(String label) {
        Objects.requireNonNull(label, "label");
        return label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
