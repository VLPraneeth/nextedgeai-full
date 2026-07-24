package com.syncari.connector;

import org.bson.Document;
import org.junit.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ValueTest {
    @Test
    public void primitivesAndStrings() {
        Value value1 = new Value(true, Optional.of("test"));
        Value value2 = new Value(true, Optional.of(1));
        Value value3 = new Value(true, Optional.of(true));
        final ZonedDateTime now = ZonedDateTime.now();
        Value value5 = new Value(true, Optional.of(now));
        final Date today = new Date();
        Value value6 = new Value(true, Optional.of(today));
        final Instant instantNow = Instant.now();
        Value value7 = new Value(true, Optional.of(instantNow));
        Value value4 = new Value(false, Optional.empty());


        assertFalse(value1.hasChange("test"));
        assertTrue(value1.hasChange("diff"));
        assertTrue(value4.hasChange("test"));
        assertFalse(value4.hasChange(null));

        assertFalse(value2.hasChange(1));
        assertTrue(value2.hasChange(2));
        assertFalse(value3.hasChange(true));
        assertTrue(value3.hasChange(false));
        assertFalse(value5.hasChange(now));
        assertTrue(value5.hasChange(now.minusDays(1)));
        assertFalse(value6.hasChange(today));
        assertTrue(value6.hasChange(new Date(today.getTime() - 1)));
        assertFalse(value7.hasChange(instantNow));
        assertTrue(value7.hasChange(Instant.now().minusMillis(1)));
    }

    @Test
    public void docsAndMapsAreSame() {
        Value value1 = new Value(true, Optional.of(new Document("k1", "v1")));
        Value value2 = new Value(true, Optional.of(Map.of("k1", "v1")));
        Value value3 = new Value(true, Optional.of(new LinkedHashMap(Map.of("k1", "v1"))));
        assertFalse(value1.hasChange(Map.of("k1", "v1")));
        assertFalse(value1.hasChange(new LinkedHashMap(Map.of("k1", "v1"))));
        assertFalse(value2.hasChange(new Document("k1", "v1")));
        assertFalse(value2.hasChange(new LinkedHashMap(Map.of("k1", "v1"))));
        assertFalse(value3.hasChange(new Document("k1", "v1")));
        assertFalse(value3.hasChange(Map.of("k1", "v1")));

    }

    @Test
    public void listsAreSame() {
        Value value1 = new Value(true, Optional.of(List.of(new Document("k1", "v1"))));
        Value value2 = new Value(true, Optional.of(List.of(Map.of("k1", "v1"))));
        assertFalse(value1.hasChange(List.of(Map.of("k1", "v1"))));
        assertFalse(value2.hasChange(List.of(new Document("k1", "v1"))));
    }

    @Test
    public void listsWithNulls() {
        List<String> values = new ArrayList<>();
        values.add(null);
        values.add("123");
        List<String> in = new ArrayList<>(values);
        List<String> other = List.of("123", "456");
        Value value1 = new Value(true, Optional.of(values));
        assertFalse(value1.hasChange(in));
        assertTrue(value1.hasChange(other));
    }

}