package cn.mythicland.dreamlib.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DreamLibApiTest {
    @Test
    void rootCauseMessageUsesTheDeepestCause() {
        Throwable error = new IllegalStateException("outer", new IllegalArgumentException("inner"));
        assertEquals("inner", DreamLibApi.rootCauseMessage(error));
    }
}
