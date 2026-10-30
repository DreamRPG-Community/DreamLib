package cn.mythicland.dreamlib.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotatedMenuViewTest {

    @Test
    void annotationTypesExposeReusableSlotAndActionMetadata() throws NoSuchMethodException {
        MenuButton button = ExampleView.class.getDeclaredMethod("button", Player.class)
                .getAnnotation(MenuButton.class);
        MenuAction action = ExampleView.class.getDeclaredMethod("action", Player.class, ClickType.class)
                .getAnnotation(MenuAction.class);

        assertEquals(4, button.slot());
        assertEquals(4, action.slot());
        assertEquals(List.of(ClickType.LEFT, ClickType.RIGHT), List.of(action.clicks()));
        assertTrue(action.playClickSound());
    }

    private static final class ExampleView extends AnnotatedMenuView {

        @MenuButton(slot = 4)
        private ItemStack button(Player player) {
            return MenuItems.button(Material.CHEST, "按钮", List.of());
        }

        @MenuAction(
                slot = 4,
                clicks = {ClickType.LEFT, ClickType.RIGHT},
                playClickSound = true
        )
        @SuppressWarnings("EmptyMethod")
        private void action(Player player, ClickType click) {
            // Metadata-only fixture.
        }

        @Override
        public String title(Player player) {
            return "测试";
        }

        @Override
        public int size(Player player) {
            return 9;
        }
    }
}
