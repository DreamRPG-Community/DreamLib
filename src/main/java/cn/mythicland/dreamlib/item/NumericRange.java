package cn.mythicland.dreamlib.item;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A finite numeric value or inclusive numeric range optionally marked as a percentage.
 */
public record NumericRange(double minimum, double maximum, boolean percent) {

    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "^([+-]?\\d+(?:\\.\\d+)?)(?:\\s*-\\s*([+-]?\\d+(?:\\.\\d+)?))?\\s*(%)?$"
    );

    /**
     * Creates a validated numeric range.
     *
     * @param minimum the inclusive lower bound
     * @param maximum the inclusive upper bound
     * @param percent whether the source value used a percent suffix
     */
    public NumericRange {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("Numeric range values must be finite");
        }
        if (minimum > maximum) {
            throw new IllegalArgumentException("Numeric range minimum cannot exceed maximum");
        }
    }

    /**
     * Parses a legacy lore number such as {@code +400-450} or {@code 30%}.
     *
     * @param text the number text
     * @return the parsed range
     * @throws NullPointerException     if {@code text} is null
     * @throws IllegalArgumentException if the number text is invalid
     */
    public static NumericRange parse(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = VALUE_PATTERN.matcher(text.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Expected a number or range, got: " + text);
        }

        double minimum = parseNumber(matcher.group(1), text);
        String maximumText = matcher.group(2);
        double maximum = maximumText == null ? minimum : parseNumber(maximumText, text);
        return new NumericRange(minimum, maximum, matcher.group(3) != null);
    }

    private static double parseNumber(String text, String source) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric value in: " + source, exception);
        }
    }

    /**
     * Returns whether this range contains a single value.
     *
     * @return true for a fixed value
     */
    public boolean isFixed() {
        return Double.compare(minimum, maximum) == 0;
    }

    /**
     * Returns the arithmetic midpoint of this range.
     *
     * @return the midpoint
     */
    public double average() {
        return minimum + (maximum - minimum) / 2.0D;
    }
}
