package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.core.TestConfig;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.*;
import com.syncari.core.pipeline.ExpressionToMapVisitor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.AttributeRepo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
@DirtiesContext
public class ListFunctionsTest {
    @Autowired
    ListFunctions functions;

    @Test
    public void firstOnEntity() throws Exception {
        EntityData record = new EntityData().addValue("domains", List.of("gmail.com", "google.com", "syncari.com"));
        GraphContext context = new GraphContext().set("previous",
                record).set("record", record).setCurrentNode(new MappingNode().setName("My Custom Node"));
        functions.firstOnEntity(List.of(),createCall("newValue","{{previous.values.domains}}"), context);
        assertEquals("gmail.com",context.get("previousValue"));
        assertEquals("gmail.com",context.get("Value From My Custom Node"));

        record.addValue("singleValued","singleValue");
        functions.firstOnEntity(List.of(),createCall("newValue","{{previous.values.singleValued}}"), context);
        assertEquals("singleValue",context.get("previousValue"));
        assertEquals("singleValue",context.get("Value From My Custom Node"));

        record.addValue("testValue",List.of("first", "second"));
        Object result = functions.firstOnEntity(List.of(record),createCall("newValue","{{record.values.testValue}}"), context);
        assertEquals("first",context.get("previousValue"));
        assertEquals("first",context.get("Value From My Custom Node"));
        assertEquals(result, record);
    }

    @Test
    public void getListItem() {
        GraphContext context = new GraphContext().setCurrentNode(new MappingNode().setName("My Custom Node"));
        Object result = functions.getListItem(null, createCall("newValue", "{{previous.values.domains}}"), context);
        assertNull(result);

        result = functions.getListItem(List.of(List.of()), createCall("position", "3"), context);
        assertNull(result);

        result = functions.getListItem(List.of(List.of(1, 2)), createCall("position", "2"), context);
        assertNull(result);

        result = functions.getListItem(List.of(List.of(1, 2)), createCall("position", "invalid"), context);
        assertNull(result);

        result = functions.getListItem(List.of(List.of(1, 2)), createCall("position", "0"), context);
        assertEquals(1, result);
        result = functions.getListItem(List.of(List.of(1, 2)), createCall("position", "1"), context);
        assertEquals(2, result);
    }

    @Test
    public void findInList() {
        GraphContext context = new GraphContext().setCurrentNode(new MappingNode().setName("My Custom Node"));
        Object result = functions.findInList(null, createCall("newValue", "{{previous.values.domains}}"), context);
        assertTrue(((List)result).isEmpty());

        result = functions.findInList(List.of(List.of()), createCall("position", "3"), context);
        assertTrue(((List)result).isEmpty());

        // no predicate
        result = functions.findInList(List.of(List.of(1, 2)), createCall("position", "2"), context);
        assertTrue(((List)result).size() == 2);

        // by value
        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "syncari_findInList_ValueInList"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 2)
        );
        result = functions.findInList(List.of(List.of(1, 2)), createCall("predicate", eq), context);
        assertTrue(((List)result).size() == 1);
        assertEquals(2, ((List)result).get(0));

        // by position
        eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", "syncari_findInList_Position"),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 1)
        );
        result = functions.findInList(List.of(List.of(1, 2)), createCall("predicate", eq), context);
        assertTrue(((List)result).size() == 1);
        assertEquals(2, ((List)result).get(0));


        // by value and position
        var exp = Expression.and(
                Expression.eq(Expression.var("syncari_findInList_ValueInList"),Expression.lit("tue")),
                Expression.gte(Expression.var("syncari_findInList_Position"),Expression.lit(1))
        );
        var visitor = new ExpressionToMapVisitor();
        exp.accept(visitor);
        var predicateMap = Map.of("predicates", List.of(visitor.getMap()), "operator", "AND");
        result = functions.findInList(List.of(List.of("mon", "tue", "wed", "thu", "tue")), createCall("predicate", predicateMap), context);
        assertTrue(((List)result).size() == 2);
        assertEquals("tue", ((List)result).get(0));

        context.put("previous", new EntityData().addValue("domains", "gmail.com"));
        exp = Expression.eq(Expression.var("syncari_findInList_ValueInList"),Expression.lit("{{previous.values.domains}}"));
        visitor = new ExpressionToMapVisitor();
        exp.accept(visitor);
        predicateMap = Map.of("predicates", List.of(visitor.getMap()), "operator", "AND");
        result = functions.findInList(List.of(List.of("microsoft.com", "gmail.com", "google.com", "gmail.com")), createCall("predicate", predicateMap), context);
        assertTrue(((List)result).size() == 2);
    }
    
    private FunctionCall createCall(Object... keyValues) {
        Map<String, Object> config = new HashMap<>();
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                config.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return new FunctionCall().setConfig(config).setParams(List.of(ParameterValue.string("param", "input")));
    }

    private FunctionCall createMultiParamCall(String... paramNames) {
        List<ParameterValue> params = Arrays.asList(paramNames).stream().map(p -> ParameterValue.string(p, "input")).collect(Collectors.toList());
        return new FunctionCall().setParams(params);
    }
}
