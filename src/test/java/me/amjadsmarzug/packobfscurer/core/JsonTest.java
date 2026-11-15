package me.amjadsmarzug.packobfscurer.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void keepsKeyOrderAndNumberText() {
        Object doc = Json.parse("{\"b\": 1.0, \"a\": [1e5, -2, true, null, \"x\\n\\u0041\"]}");
        assertEquals("{\"b\":1.0,\"a\":[1e5,-2,true,null,\"x\\nA\"]}", Json.write(doc, false));
        assertEquals(List.of("b", "a"), List.copyOf(((Map<?, ?>) doc).keySet()));
    }

    @Test
    void roundTripsEscapes() {
        String text = "{\"s\":\"quote \\\" slash \\\\ tab \\t\"}";
        Object doc = Json.parse(text);
        assertEquals(text, Json.write(doc, false));
    }

    @Test
    void prettyPrintsWithTwoSpaces() {
        String pretty = Json.write(Json.parse("{\"a\":{\"b\":[1]}}"), true);
        assertEquals("{\n  \"a\": {\n    \"b\": [\n      1\n    ]\n  }\n}\n", pretty);
    }

    @Test
    void reportsLineAndColumnOnError() {
        Json.ParseException ex = assertThrows(Json.ParseException.class,
                () -> Json.parse("{\n  \"a\": ,\n}"));
        assertTrue(ex.getMessage().contains("line 2"), ex.getMessage());
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(Json.ParseException.class, () -> Json.parse("{} x"));
    }
}
