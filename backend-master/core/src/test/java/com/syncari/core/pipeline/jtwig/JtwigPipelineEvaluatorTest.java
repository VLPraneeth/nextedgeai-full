package com.syncari.core.pipeline.jtwig;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.syncari.core.utils.GraphHelper.createConnector;
import static com.syncari.core.utils.GraphHelper.newGraph;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class JtwigPipelineEvaluatorTest extends AbstractSyncariTest {

    @Autowired
    JTwigPipelineEvaluator jTwigPipelineEvaluator;
    @Test
    public void testTraversal() {


    }
}
