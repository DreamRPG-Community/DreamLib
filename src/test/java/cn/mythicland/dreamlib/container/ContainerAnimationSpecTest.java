package cn.mythicland.dreamlib.container;

import org.bukkit.Sound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the vanilla ender-chest animation contract without starting Bukkit.
 */
class ContainerAnimationSpecTest {

    @Test
    void enderChestUsesTheVanillaBlockActionAndSounds() {
        ContainerAnimationSpec specification = ContainerAnimationSpec.enderChest();

        assertEquals(1, specification.blockAction());
        assertEquals(Sound.BLOCK_ENDERCHEST_OPEN, specification.openSound());
        assertEquals(Sound.BLOCK_ENDERCHEST_CLOSE, specification.closeSound());
        assertEquals(1.0F, specification.openVolume());
        assertEquals(1.0F, specification.openPitch());
        assertEquals(1.0F, specification.closeVolume());
        assertEquals(0.8F, specification.closePitch());
        assertEquals(64.0D, specification.broadcastDistance());
    }

    @Test
    void negativeBroadcastDistanceIsRejectedBeforePacketUse() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContainerAnimationSpec(
                        1,
                        Sound.BLOCK_ENDERCHEST_OPEN,
                        Sound.BLOCK_ENDERCHEST_CLOSE,
                        1.0F,
                        1.0F,
                        1.0F,
                        0.8F,
                        -1.0D
                )
        );
    }
}
