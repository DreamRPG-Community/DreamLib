package cn.mythicland.dreamlib.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies strict database payload validation before Bukkit item application.
 */
class ItemStackArrayCodecTest {

    @Test
    void blankPayloadIsRejectedInsteadOfBecomingAnEmptyInventory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemStackArrayCodec.deserialize(" ", 54)
        );
    }

    @Test
    void malformedBase64IsRejectedBeforeDeserialization() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemStackArrayCodec.deserialize("not-base64", 54)
        );
    }
}
