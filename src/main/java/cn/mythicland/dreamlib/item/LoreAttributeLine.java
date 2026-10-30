package cn.mythicland.dreamlib.item;

import java.util.Objects;

/**
 * One successfully parsed generic lore attribute line.
 *
 * @param label      the color-stripped label as written in the lore
 * @param labelColor the first effective color code before the label, or an empty string
 * @param value      the parsed numeric value or range
 * @param source     the original lore line
 */
public record LoreAttributeLine(String label, String labelColor, NumericRange value, String source) {

    public LoreAttributeLine {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(labelColor, "labelColor");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");
    }
}
