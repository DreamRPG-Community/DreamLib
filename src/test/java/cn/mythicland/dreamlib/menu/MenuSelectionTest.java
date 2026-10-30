package cn.mythicland.dreamlib.menu;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenuSelectionTest {

    @Test
    void selectedAndUnselectedOptionsUseTheSharedSettingsFormat() {
        List<String> lore = MenuSelection.lore(
                "选择刷新方式。",
                List.of("定时刷新", "始终刷新"),
                1,
                List.of("&7额外说明")
        );

        assertEquals("&7选择刷新方式。", lore.get(0));
        assertEquals("&7  &7定时刷新", lore.get(2));
        assertEquals("&a▶ &a始终刷新", lore.get(3));
        assertEquals("&e点击切换!", lore.getLast());
    }

    @Test
    void onlyPlainLeftAndRightClicksHaveSelectionDirections() {
        assertTrue(MenuSelection.isCycleClick(ClickType.LEFT));
        assertTrue(MenuSelection.isCycleClick(ClickType.RIGHT));
        assertEquals(1, MenuSelection.direction(ClickType.LEFT));
        assertEquals(-1, MenuSelection.direction(ClickType.RIGHT));
        assertFalse(MenuSelection.isCycleClick(ClickType.SHIFT_LEFT));
        assertEquals(0, MenuSelection.direction(ClickType.DOUBLE_CLICK));
    }
}
