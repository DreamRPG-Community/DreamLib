package cn.mythicland.dreamlib.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageWindowTest {

    @Test
    void clampsEmptyAndOutOfRangePages() {
        PageWindow empty = PageWindow.of(0, 45, 8);
        assertEquals(0, empty.page());
        assertEquals(1, empty.pageCount());
        assertEquals(0, empty.startIndex());
        assertEquals(0, empty.endIndex());
        assertFalse(empty.hasPrevious());
        assertFalse(empty.hasNext());

        PageWindow last = PageWindow.of(46, 45, 9);
        assertEquals(1, last.page());
        assertEquals(2, last.pageCount());
        assertEquals(45, last.startIndex());
        assertEquals(46, last.endIndex());
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
    }

    @Test
    void rejectsInvalidPageSizes() {
        assertThrows(IllegalArgumentException.class, () -> PageWindow.of(-1, 45, 0));
        assertThrows(IllegalArgumentException.class, () -> PageWindow.of(1, 0, 0));
    }
}
