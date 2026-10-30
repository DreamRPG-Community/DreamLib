package cn.mythicland.dreamlib.web;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonCodecTest {

    @Test
    void parsesAndWritesNestedJsonWithoutExternalDependency() {
        Map<String, Object> value = JsonCodec.parseObject("{\"name\":\"中文\\n值\",\"count\":2,\"items\":[true,null]}");

        assertEquals("中文\n值", value.get("name"));
        assertEquals(2L, value.get("count"));
        assertEquals(Arrays.asList(true, null), value.get("items"));
        assertEquals("{\"name\":\"中文\\n值\",\"count\":2,\"items\":[true,null]}", JsonCodec.stringify(value));
    }

    @Test
    void rejectsTrailingJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonCodec.parse("true false"));
    }
}
