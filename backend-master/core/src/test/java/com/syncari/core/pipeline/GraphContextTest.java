package com.syncari.core.pipeline;

import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.token.JtwigModelSanitizer;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class GraphContextTest {

    @Test
    public void subContextGetTraversesParents() {
        GraphContext parentContext = new GraphContext();
        for (int i = 0; i < 100; i++) {
            parentContext.put("key" + i, "value" + i);
        }
        final GraphContext subContext = parentContext.createSubContext(new MappingGraph());
        assertEquals(parentContext.size(), subContext.size());
        assertEquals("value25", subContext.get("key25"));
        assertEquals("value25", parentContext.get("key25"));
        parentContext.put("keyAfterSubContext", "newValue");
        assertEquals("newValue", subContext.get("keyAfterSubContext"));
        assertTrue(subContext.containsKey("key25"));
        assertTrue(subContext.containsValue("value25"));
        assertEquals("newValue", parentContext.get("keyAfterSubContext"));
        //removing value from subcontext doesnt affect parent
        subContext.remove("keyAfterSubContext");
        assertEquals("newValue", parentContext.get("keyAfterSubContext"));
        assertTrue(subContext.containsKey("keyAfterSubContext"));
        assertTrue(subContext.containsValue("newValue"));
        //removing a key in parent  removes it in child
        parentContext.remove("key25");
        assertFalse(subContext.containsKey("key25"));
        AtomicInteger ct = new AtomicInteger(0);
        subContext.forEach((k, v) -> ct.incrementAndGet());
        assertEquals(subContext.size(), ct.get());
        assertEquals(subContext.size(), subContext.values().size());
        assertEquals(subContext.size(), subContext.keySet().size());
        //100 kyes + the "syncari" namespace key
        assertEquals(101, subContext.size());
    }

    @Test
    public void subContextSanitizationFixesParentKeysForJTwig() {
        GraphContext parentContext = new GraphContext();
        parentContext.put("Key With Space", "value");
        final GraphContext subContext = parentContext.createSubContext(new MappingGraph());
        final JtwigModelSanitizer jtwigModelSanitizer = JtwigModelSanitizer.newModel(subContext);
        assertEquals("value", jtwigModelSanitizer.getValues().get("Key_With_Space"));
    }

    @Test
    public void subContextRetainsTempVars() {
        GraphContext parentContext = new GraphContext();
        parentContext.setTempVariable("TempKey", "tempVal");
        final GraphContext subContext = parentContext.createSubContext(new MappingGraph());
        assertEquals("tempVal", subContext.getTempVariables().get("TempKey"));
        subContext.setTempVariable("TempKey", "tempVal2");
        subContext.setTempVariable("TempKey2", "tempVal3");
        assertEquals("tempVal2", parentContext.getTempVariables().get("TempKey"));
        assertEquals("tempVal3", parentContext.getTempVariables().get("TempKey2"));
    }

    @Test
    public void copiesAreThreadSafe() throws InterruptedException {
        MappingNode currentNode = new MappingNode();
        MappingGraph graph = new MappingGraph();
        GraphContext graphContext = new GraphContext().setTestMode(true).setCurrentNode(currentNode).setGraph(graph);
        GraphContext copy = graphContext.copy();
        Runnable mutateContext1 = () ->{
            for(int i=0;i<1000;i++){
                graphContext.forEach((k,v)->{});
                graphContext.put("newKey"+i,"newValue"+i);
            }
        };
        Runnable mutateContext2 = () ->{
            for(int i=0;i<1000;i++){
                copy.forEach((k,v)->{});
                copy.put("newKey"+i,"newValue"+i);
            }
        };

        Thread thread1 = new Thread(mutateContext1);
        Thread thread2 = new Thread(mutateContext2);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertEquals(1001,copy.size());
        assertEquals(1001,graphContext.size());
    }

}