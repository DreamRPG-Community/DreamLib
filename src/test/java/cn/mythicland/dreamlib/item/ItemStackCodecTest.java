package cn.mythicland.dreamlib.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStackCodecTest {

    @Test
    void validatesCodecInputsWithoutAConfiguredBukkitServer() {
        assertThrows(NullPointerException.class, () -> ItemStackCodec.serialize(null));
        assertThrows(NullPointerException.class, () -> ItemStackCodec.deserialize(null));
    }
}
