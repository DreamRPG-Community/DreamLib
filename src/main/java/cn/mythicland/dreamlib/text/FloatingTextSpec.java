package cn.mythicland.dreamlib.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable specification for client-side floating text.
 *
 * @param lines        lines rendered from top to bottom
 * @param lineSpacings vertical gap after each line; index {@code 0} is the required zero base
 * @param viewDistance maximum distance at which a player receives the packets
 */
public record FloatingTextSpec(
        List<String> lines,
        List<Double> lineSpacings,
        double viewDistance
) {

    /**
     * Creates a specification with one uniform gap between every adjacent line.
     *
     * @param lines        lines rendered from top to bottom
     * @param lineSpacing  positive vertical distance between adjacent lines in blocks
     * @param viewDistance maximum distance at which a player receives the packets
     */
    public FloatingTextSpec(
            List<String> lines,
            double lineSpacing,
            double viewDistance
    ) {
        this(lines, uniformLineSpacings(lines, lineSpacing), viewDistance);
    }

    /**
     * Validates and defensively copies the specification.
     *
     * @throws NullPointerException     if {@code lines}, a line, or a spacing is null
     * @throws IllegalArgumentException if the line count, line spacing, or view distance is invalid
     */
    public FloatingTextSpec {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty() || lines.size() > 16) {
            throw new IllegalArgumentException("Floating text must contain between one and sixteen lines");
        }
        lines = lines.stream()
                .map(line -> Objects.requireNonNull(line, "line"))
                .toList();
        Objects.requireNonNull(lineSpacings, "lineSpacings");
        if (lineSpacings.size() != lines.size()) {
            throw new IllegalArgumentException("lineSpacings must match the number of lines");
        }
        List<Double> validatedSpacings = new ArrayList<>(lineSpacings.size());
        for (int index = 0; index < lineSpacings.size(); index++) {
            Double spacing = Objects.requireNonNull(lineSpacings.get(index), "line spacing");
            if (!Double.isFinite(spacing) || spacing < 0.0D) {
                throw new IllegalArgumentException("lineSpacings must be finite and non-negative");
            }
            if (index == 0 && Double.compare(spacing, 0.0D) != 0) {
                throw new IllegalArgumentException("lineSpacings[0] must be zero");
            }
            validatedSpacings.add(spacing);
        }
        lineSpacings = List.copyOf(validatedSpacings);
        if (!Double.isFinite(viewDistance) || viewDistance <= 0.0D) {
            throw new IllegalArgumentException("viewDistance must be finite and positive");
        }
    }

    private static List<Double> uniformLineSpacings(List<String> lines, double lineSpacing) {
        Objects.requireNonNull(lines, "lines");
        if (!Double.isFinite(lineSpacing) || lineSpacing <= 0.0D) {
            throw new IllegalArgumentException("lineSpacing must be finite and positive");
        }
        List<Double> spacings = new ArrayList<>(Collections.nCopies(lines.size(), lineSpacing));
        if (!spacings.isEmpty()) spacings.set(0, 0.0D);
        return spacings;
    }

    /**
     * Returns the first adjacent-line gap for callers that only support uniform spacing.
     *
     * @return first configured adjacent-line gap, or zero for a single-line display
     */
    public double lineSpacing() {
        return lineSpacings.size() > 1 ? lineSpacings.get(1) : 0.0D;
    }
}
