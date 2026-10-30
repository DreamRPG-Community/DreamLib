package cn.mythicland.dreamlib.text;

import net.md_5.bungee.api.chat.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandSuggestionTest {
    @Test
    void suggestionDoesNotExecuteAndPreservesLiteralItemColorCodes() {
        String command = "/drpg items name story &a故事起源";
        var component = LegacyText.commandSuggestion("&e点击填写", command);
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, component.getClickEvent().getAction());
        assertEquals(command, component.getClickEvent().getValue());
        assertEquals("点击填写", component.toPlainText());
    }

    @Test
    void rejectsCommandsThatCouldBecomeMultipleChatLines() {
        assertThrows(IllegalArgumentException.class, () -> LegacyText.commandSuggestion("edit", "/drpg\nstop"));
    }
}
