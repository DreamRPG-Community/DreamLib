package cn.mythicland.dreamlib.item;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of parsing one generic lore line.
 */
public record LoreAttributeParseResult(
        Status status,
        Optional<String> label,
        Optional<LoreAttributeLine> attribute,
        Optional<String> error
) {

    public LoreAttributeParseResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(error, "error");
        if (status == Status.VALID && attribute.isEmpty()) {
            throw new IllegalArgumentException("A valid result must contain an attribute");
        }
        if (status == Status.INVALID && error.isEmpty()) {
            throw new IllegalArgumentException("An invalid result must contain an error");
        }
    }

    /**
     * Creates a result for a non-attribute display line.
     *
     * @return a non-attribute result
     */
    public static LoreAttributeParseResult notAttribute() {
        return new LoreAttributeParseResult(
                Status.NOT_ATTRIBUTE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates a valid result.
     *
     * @param attribute the parsed attribute
     * @return a valid result
     */
    public static LoreAttributeParseResult valid(LoreAttributeLine attribute) {
        return new LoreAttributeParseResult(
                Status.VALID,
                Optional.of(attribute.label()),
                Optional.of(attribute),
                Optional.empty()
        );
    }

    /**
     * Creates an invalid result while preserving the detected label when available.
     *
     * @param label  the detected label, or null
     * @param reason the concise parse failure reason
     * @return an invalid result
     */
    public static LoreAttributeParseResult invalid(String label, String reason) {
        return new LoreAttributeParseResult(
                Status.INVALID,
                Optional.ofNullable(label),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(reason, "reason"))
        );
    }

    /**
     * Returns whether a valid attribute was parsed.
     *
     * @return true when the status is valid
     */
    public boolean isValid() {
        return status == Status.VALID;
    }

    /**
     * Parsing state for a lore line.
     */
    public enum Status {
        NOT_ATTRIBUTE,
        VALID,
        INVALID
    }
}
