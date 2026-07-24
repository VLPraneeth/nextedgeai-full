package com.syncari.core.pipeline.expression;

import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PredicateParserTest {

    @Test
    public void testDFIPredicateParser(){
        /*
        {
            predicates=[
                {left= {value=Field Value, type=variable, datatype=string, label=description (description)},
                 operator=not_empty,
                 right={type=literal, value=},
                 predicateId=682590b0d54679a8ced530e2}
            ],
            groupPredicateId=682590b0d54679a8ced530e3,
            operator=AND
        }
        */
        PredicateParser parser = new PredicateParser();
        Map<String, Object> config = new HashMap<>();
        config.put("predicates", List.of(
                Map.of(
                        "left", Map.of(
                                "value", "66b3d09f0cca8127124e0ea3",
                                "type", "variable",
                                "datatype", "string",
                                "label", "description (description)"
                        ),
                        "operator", "not_empty",
                        "right", Map.of(
                                "type", "literal",
                                "value", ""
                        ),
                        "predicateId", "682590b0d54679a8ced530e2"
                )
        ));
        config.put("groupPredicateId", "682590b0d54679a8ced530e3");
        config.put("operator", "AND");
        AttributeDefinition testAttr = new AttributeDefinition().setApiName("description").setDisplayName("description (description)");
        testAttr.setId("66b3d09f0cca8127124e0ea3");
        testAttr.setDataType(new StringType());
        parser.fromDFIRuleConfig("recId", testAttr, config);
        assertEquals(parser.fromDFIRuleConfig("recId", testAttr, config), parser.fromMap(config));

        Map<String, Object> config1 = new HashMap<>();
        config1.put("predicates", List.of(
                Map.of(
                        "left", Map.of(
                                "value", "field_value",
                                "type", "variable",
                                "datatype", "string",
                                "label", "description (description)"
                        ),
                        "operator", "not_empty",
                        "right", Map.of(
                                "type", "literal",
                                "value", ""
                        ),
                        "predicateId", "682590b0d54679a8ced530e2"
                )
        ));
        config1.put("groupPredicateId", "682590b0d54679a8ced530e3");
        config1.put("operator", "AND");
        assertEquals(parser.fromDFIRuleConfig("recId", testAttr, config1), parser.fromMap(config));
    }
}
