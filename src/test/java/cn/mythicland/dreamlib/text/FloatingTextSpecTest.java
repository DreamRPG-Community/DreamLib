package cn.mythicland.dreamlib.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies packet display validation without starting a server.
 */
class FloatingTextSpecTest {

    @Test
    void linesAreDefensivelyCopiedAndPreserveOrder() {
        List<String> source = new ArrayList<>(List.of("&7timer", "&aavailable"));
        FloatingTextSpec specification = new FloatingTextSpec(source, 0.25D, 32.0D);
        source.set(0, "&cchanged");

        assertEquals(List.of("&7timer", "&aavailable"), specification.lines());
        assertEquals(List.of(0.0D, 0.25D), specification.lineSpacings());
    }

    @Test
    void preservesIndependentLineSpacings() {
        FloatingTextSpec specification = new FloatingTextSpec(
                List.of("top", "middle", "bottom"),
                List.of(0.0D, 0.4D, 0.6D),
                32.0D
        );

        assertEquals(List.of(0.0D, 0.4D, 0.6D), specification.lineSpacings());
    }

    @Test
    void invalidDisplayGeometryIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FloatingTextSpec(List.of("line"), 0.0D, 32.0D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FloatingTextSpec(List.of("line"), 0.25D, -1.0D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FloatingTextSpec(
                        List.of("top", "bottom"),
                        List.of(0.25D, 0.4D),
                        32.0D
                )
        );
    }
}
