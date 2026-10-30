package cn.mythicland.dreamlib.menu;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Shared four-by-seven content grid surrounded by side columns and header/footer rows.
 * Slots are zero-based; the footer contains previous, back and next controls.
 */
public final class MenuPageLayout {
    public static final int SIZE = 54;
    public static final int PREVIOUS_SLOT = 45;
    public static final int BACK_SLOT = 49;
    public static final int NEXT_SLOT = 53;
    public static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );

    private MenuPageLayout() {
    }

    /**
     * Visits the entries in a page with their corresponding inventory slots.
     *
     * @param entries all entries in display order
     * @param requestedPage zero-based page to display
     * @param visitor receives the inventory slot and the entry
     * @param <T> entry type
     * @return the clamped page window
     */
    public static <T> PageWindow forEachEntry(List<T> entries, int requestedPage, BiConsumer<Integer, T> visitor) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(visitor, "visitor");
        PageWindow window = PageWindow.of(entries.size(), CONTENT_SLOTS.size(), requestedPage);
        for (int index = window.startIndex(); index < window.endIndex(); index++) {
            visitor.accept(CONTENT_SLOTS.get(index - window.startIndex()), entries.get(index));
        }
        return window;
    }

    /**
     * Returns a chest size with one to four seven-column content rows and two reserved rows.
     * This pure calculation is thread-safe and retains a full page for paginated content.
     *
     * @param entryCount total number of entries, including entries on subsequent pages
     * @return inventory size between 27 and 54 slots
     * @throws IllegalArgumentException if the entry count is negative
     */
    public static int compactSize(int entryCount) {
        if (entryCount < 0) throw new IllegalArgumentException("Entry count must not be negative");
        int rows = Math.max(1, (Math.min(entryCount, CONTENT_SLOTS.size()) + 6) / 7);
        return (rows + 2) * 9;
    }
}
