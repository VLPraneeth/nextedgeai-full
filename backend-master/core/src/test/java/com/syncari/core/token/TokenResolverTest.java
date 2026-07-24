package com.syncari.core.token;

import com.syncari.core.pipeline.GraphContext;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TokenResolverTest {

    private SyncariTokenResolver tokenResolverWithXPath = new SyncariTokenResolver() {
    };
    private SyncariTokenResolver tokenResolver = new SyncariTokenResolver() {
        protected boolean isXPathTokenEnabled() {
            return false;
        }

    };

    @Test
    public void testSimpleCase() {

        GraphContext map = new GraphContext();
        map.put("foo", "bar");
        TokenResolution result = tokenResolver.resolve("{{foo}}", map);
        assertEquals("bar", result.getResolvedValue());
    }

    @Test
    public void testNestedMap() {
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("baz", "qux");

        GraphContext map = new GraphContext();
        map.put("foo", innerMap);

        TokenResolution result = tokenResolver.resolve("{{foo.baz}}", map);

        assertEquals("qux", result.getResolvedValue());
    }

    @Test
    public void testArrayAccess() {
        Object[] array = new Object[]{"foo", "bar", "baz"};

        GraphContext map = new GraphContext();
        map.put("array", array);

        TokenResolution result = tokenResolver.resolve("{{array[1]}}", map);

        assertEquals("bar", result.getResolvedValue());
        Map<String, Object> jsonObject = Map.of("success", true, "result", List.of(Map.of("name", "John Doe", "email", "john@syncari.com"),
                Map.of("name", "Jane Doe", "email", "jane@syncari.com")));

        GraphContext graphContext = new GraphContext().set("response",
                jsonObject);
        String token = "{{response.result[0].name}}";
        assertEquals("John Doe", tokenResolver.resolve(token, graphContext).getResolvedValue());

        token = "{{response.result[0].email}}";
        assertEquals("john@syncari.com", tokenResolver.resolve(token, graphContext).getResolvedValue());

        token = "{{response.result[1].name}}";
        assertEquals("Jane Doe", tokenResolver.resolve(token, graphContext).getResolvedValue());

        token = "{{response.result[1].email}}";
        assertEquals("jane@syncari.com", tokenResolver.resolve(token, graphContext).getResolvedValue());

        token = "{{response.result[2].name}}";
        assertNull(tokenResolver.resolve(token, graphContext).getResolvedValue());

    }

    @Test
    public void testStringRendering() {
        Map<String, Object> jsonObject = Map.of("success", true, "result", List.of(Map.of("name", "John Doe", "email", "john@syncari.com"),
                Map.of("name", "Jane Doe", "email", "jane@syncari.com")));

        GraphContext graphContext = new GraphContext().set("response",
                jsonObject);
        String token = "string rendering single token {{response.result[1].email}} with additioanl text";
        assertEquals("string rendering single token jane@syncari.com with additioanl text", tokenResolver.resolve(token, graphContext).getResolvedValue());
        token = "string rendering two token {{response.result[1].email}} -- {{response.result[1].name}} with additioanl text";
        assertEquals("string rendering two token jane@syncari.com -- Jane Doe with additioanl text", tokenResolver.resolve(token, graphContext).getResolvedValue());
        Map<String, Object> lookup = Map.of("values", Map.of("Address_Line1__c", "A1", "Address_Line3__c", "A3"));
        GraphContext context = new GraphContext().set("previousLookup",
                lookup);

        assertEquals("A1NEWLINE", tokenResolver.resolve("{{previousLookup.values.Address_Line1__c}}NEWLINE{{previousLookup.values.Address_Line2__c}}", context).getResolvedValue());
        assertEquals("A1\n", tokenResolver.resolve("{{previousLookup.values.Address_Line1__c}}\n{{previousLookup.values.Address_Line2__c}}", context).getResolvedValue());
        assertEquals("A1\nA3", tokenResolver.resolve("{{previousLookup.values.Address_Line1__c}}\n{{previousLookup.values.Address_Line3__c}}", context).getResolvedValue());

        assertEquals("health score: A1", tokenResolver.resolve("health score: {{previousLookup.values.Address_Line1__c}}", context).getResolvedValue());
        assertEquals("NEWLINE", tokenResolver.resolve("{{previousLookup.values.Address_Line4__c}}NEWLINE{{previousLookup.values.Address_Line2__c}}", context).getResolvedValue());
    }

    @Test
    public void testNonTokenResolution() {
        Map<String, Object> jsonObject = Map.of("success", true, "result", List.of(Map.of("name", "John Doe", "email", "john@syncari.com"),
                Map.of("name", "Jane Doe", "email", "jane@syncari.com")));

        GraphContext graphContext = new GraphContext().set("response",
                jsonObject);
        String token = "rendering simple string";
        assertEquals("rendering simple string", tokenResolver.resolve(token, graphContext).getResolvedValue());
    }

    @Test
    public void testObjectArrayAccess() {
        Object[] array = new Object[]{
                new TestObject("foo", 1),
                new TestObject("bar", 2),
                new TestObject("baz", 3)
        };

        GraphContext map = new GraphContext();
        map.put("array", array);

        TokenResolution result = tokenResolver.resolve("{{array[1].name}}", map);

        assertEquals("bar", result.getResolvedValue());
    }

    @Test
    public void testObjectFieldAccess() {
        TestObject obj = new TestObject("foo", 42);

        GraphContext map = new GraphContext();
        map.put("obj", obj);

        TokenResolution result = tokenResolver.resolve("{{obj.value}}", map);

        assertEquals(42, result.getResolvedValue());
    }

    @Test
    public void testEscapeDot() {
        GraphContext map = new GraphContext();
        map.put("foo\\.bar", "baz");

        TokenResolution result = tokenResolver.resolve("{{foo\\.bar}}", map);

        assertEquals("baz", result.getResolvedValue());
    }

    @Test
    public void testComplexExpression() {
        GraphContext map = new GraphContext();
        map.put("key", Map.of("foo", List.of(Map.of("bar", "baz"))));

        TokenResolution result = tokenResolver.resolve("{{key.foo[0].bar}}", map);
        assertEquals("baz", result.getResolvedValue());

        map = new GraphContext();
        map.put("key", Map.of("foo", List.of(Map.of("bar", List.of(Map.of("baz", "aux"))))));

        result = tokenResolver.resolve("{{key.foo[0].bar[0].baz}}", map);

        assertEquals("aux", result.getResolvedValue());
    }

    @Test
    public void testSanitizeString() {

        assertEquals("foo.bar", tokenResolver.sanitizedToken("foo.bar"));
        assertEquals("foo.bar", tokenResolver.sanitizedToken("foo.\nbar"));
        assertEquals("foo.bar", tokenResolver.sanitizedToken("foo.\r\nbar"));
        assertEquals("foo.bar", tokenResolver.sanitizedToken("foo.\r\nbar\n"));
        assertEquals("foo.bar", tokenResolver.sanitizedToken("foo.bar\n"));
    }

    @Test
    public void testXPath() {
        GraphContext map = new GraphContext();
        final Map<String, String> bar = Map.of("bar", "bazar", "another", "val");
        map.put("key", Map.of("foo", List.of(
                Map.of("bar", "baz", "First Name", "Demo"),
                bar
        )));

        TokenResolution result = tokenResolverWithXPath.resolve("{{/key/foo[0]/bar}}", map);
        assertEquals("baz", result.getResolvedValue());
        result = tokenResolverWithXPath.resolve("{{/key/foo[(@bar = 'bazar')]/another[0]}}", map);
        assertEquals("val", result.getResolvedValue());
        result = tokenResolverWithXPath.resolve("{{/key/foo[(@bar = 'bazar')]}}", map);
        assertEquals(bar, result.getResolvedValue());
        result = tokenResolverWithXPath.resolve("{{/key/foo[contains(@bar,'baza')]}}", map);
        assertEquals(bar, result.getResolvedValue());
        result = tokenResolverWithXPath.resolve("{{/key/foo[bar='baz'][@name='First Name']}}", map);
        assertEquals("Demo", result.getResolvedValue());
        result = tokenResolverWithXPath.resolve("{{/key/foo[bar='baz'][@name='ABC']}}", map);
        assertNull(result.getResolvedValue());
    }

    @Test
    public void testXPathVector() {
        GraphContext map = new GraphContext();

        var map1 = Map.of("id", "123", "title", "ABC", "name", "XYZ");
        var map2 = Map.of("id", "123", "title", "EFG", "name", "TUV");

        var obj = List.of(map1, map2);
        map.put("key", List.of(Map.of("id", "123", "title", "ABC", "name", "XYZ"), Map.of("id", "123", "title", "EFG", "name", "TUV")));
        assertEquals(List.of("ABC", "EFG"), tokenResolverWithXPath.resolve("{{/key/title}}", map).getResolvedValue());
    }

    @Test
    public void testUnsupportedCharsReturnsSpecialValue() {
        assertTrue(tokenResolver.resolve("{{This:Token}}", Map.of()).hasTokenSyntaxErrors());
    }

    private static class TestObject {
        private String name;
        private int value;

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }


}

