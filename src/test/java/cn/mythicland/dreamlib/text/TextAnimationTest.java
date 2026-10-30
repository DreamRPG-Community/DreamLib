package cn.mythicland.dreamlib.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextAnimationTest {

    @Test
    void mythicThePitHoldSuffixRepeatsOneFrameWithoutCopyingConfigurationText() {
        TextAnimation animation = TextAnimation.parse(List.of("&aDream [x3]", "&bRPG"));

        assertEquals(4L, animation.totalHoldTicks());
        assertEquals("&aDream", animation.frameAt(0L).text());
        assertEquals("&aDream", animation.frameAt(2L).text());
        assertEquals("&bRPG", animation.frameAt(3L).text());
    }

    @Test
    void emptyAnimationIsRejectedBeforePluginStartup() {
        assertThrows(IllegalArgumentException.class, () -> TextAnimation.parse(List.of()));
    }
}
