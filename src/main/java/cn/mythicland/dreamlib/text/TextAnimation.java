package cn.mythicland.dreamlib.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable animation sequence using the MythicThePit-compatible {@code [xN]} suffix.
 */
public final class TextAnimation {

    private static final Pattern HOLD_SUFFIX = Pattern.compile("^(.+?)\\s*\\[x(\\d+)]$");

    private final List<TextAnimationFrame> frames;
    private final long totalHoldTicks;

    private TextAnimation(List<TextAnimationFrame> frames) {
        this.frames = List.copyOf(frames);
        this.totalHoldTicks = frames.stream()
                .mapToLong(TextAnimationFrame::holdTicks)
                .reduce(0L, Math::addExact);
    }

    /**
     * Parses raw animation entries.
     *
     * @param entries raw configuration entries
     * @return immutable animation
     */
    public static TextAnimation parse(Collection<String> entries) {
        Objects.requireNonNull(entries, "entries");
        List<TextAnimationFrame> parsed = new ArrayList<>();
        for (String entry : entries) parsed.add(parseEntry(entry));
        if (parsed.isEmpty()) throw new IllegalArgumentException("Animation entries cannot be empty");
        return new TextAnimation(parsed);
    }

    private static TextAnimationFrame parseEntry(String rawEntry) {
        String entry = Objects.requireNonNull(rawEntry, "animation entry");
        Matcher matcher = HOLD_SUFFIX.matcher(entry);
        if (!matcher.matches()) return new TextAnimationFrame(entry, 1);

        String text = matcher.group(1);
        int holdTicks;
        try {
            holdTicks = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Animation hold count is invalid: " + rawEntry, exception);
        }
        return new TextAnimationFrame(text, holdTicks);
    }

    /**
     * Returns the parsed frames.
     *
     * @return immutable frame list
     */
    public List<TextAnimationFrame> frames() {
        return frames;
    }

    /**
     * Returns the frame visible at one non-negative animation tick.
     *
     * @param animationTick elapsed animation tick
     * @return selected frame
     */
    public TextAnimationFrame frameAt(long animationTick) {
        if (animationTick < 0L) throw new IllegalArgumentException("animationTick cannot be negative");
        long offset = animationTick % totalHoldTicks;
        for (TextAnimationFrame frame : frames) {
            if (offset < frame.holdTicks()) return frame;
            offset -= frame.holdTicks();
        }
        throw new IllegalStateException("Animation frame could not be resolved");
    }

    /**
     * Returns the total number of ticks in one animation cycle.
     *
     * @return cycle length
     */
    public long totalHoldTicks() {
        return totalHoldTicks;
    }
}
