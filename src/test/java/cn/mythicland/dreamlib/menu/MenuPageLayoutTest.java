package cn.mythicland.dreamlib.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuPageLayoutTest {
    @Test
    void compactPagesKeepTheirMarginsAndExpandAtSevenEntryBoundaries() {
        int[][] cases = {
                {0, 27}, {1, 27}, {7, 27}, {8, 36}, {14, 36},
                {15, 45}, {21, 45}, {22, 54}, {28, 54}, {29, 54}, {Integer.MAX_VALUE, 54}
        };
        for (int[] boundary : cases) {
            assertEquals(boundary[1], MenuPageLayout.compactSize(boundary[0]),
                    "Menu size for " + boundary[0] + " entries");
        }
    }

    @Test
    void negativeEntryCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MenuPageLayout.compactSize(-1));
    }
    @Test
    void visitsOnlyEntriesOnTheClampedPageUsingTheirContentSlots() {
        List<Integer> entries = IntStream.range(0, 31).boxed().toList();
        List<String> visited = new ArrayList<>();
        PageWindow window = MenuPageLayout.forEachEntry(entries, 99,
                (slot, entry) -> visited.add(slot + ":" + entry));
        assertEquals(1, window.page());
        assertEquals(List.of("10:28", "11:29", "12:30"), visited);
        visited.clear();
        MenuPageLayout.forEachEntry(List.<Integer>of(), 0, (slot, entry) -> visited.add("unexpected"));
        assertEquals(List.of(), visited);
    }
}
