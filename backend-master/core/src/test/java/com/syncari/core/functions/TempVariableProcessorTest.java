package com.syncari.core.functions;

import com.syncari.connector.EntityData;
import com.syncari.core.TestConfig;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.pipeline.GraphContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class TempVariableProcessorTest {
    @Autowired
    TempVariableProcessor processor;

    @Test
    public void setToEmptyList() {
        final FunctionCall functionCall = new FunctionCall();

        Map<String, Object> config = Map.of(
                "setValueField", Map.of(
                        "type", "temporary",
                        "dataType", "object",
                        "displayName", "List",
                        "apiName", "list",
                        "multiValueField", true),

                "newValue", "",
                "useEmpty", false
        );
        functionCall.setConfig(config);
        final GraphContext context = new GraphContext();
        processor.process(List.of(new EntityData()),
                functionCall, context);
        assertEquals(0, ((List) context.getTempVariables().get("list")).size());
    }
}