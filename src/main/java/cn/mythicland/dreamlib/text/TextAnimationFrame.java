package cn.mythicland.dreamlib.text;

import java.util.Objects;

/**
 * One immutable text animation frame.
 *
 * @param text      raw text before legacy color conversion
 * @param holdTicks number of animation ticks for which the frame remains visible
 */
public record TextAnimationFrame(String text, int holdTicks) {

    /**
     * Validates one animation frame.
     */
    public TextAnimationFrame {
        text = Objects.requireNonNull(text, "text");
        if (text.isBlank()) throw new IllegalArgumentException("Animation frame text cannot be blank");
        if (holdTicks < 1) throw new IllegalArgumentException("Animation frame holdTicks must be positive");
    }
}
