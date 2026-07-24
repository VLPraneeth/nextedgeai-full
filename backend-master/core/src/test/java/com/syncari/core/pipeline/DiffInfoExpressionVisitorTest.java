package com.syncari.core.pipeline;

import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.pipeline.expression.VariableExpression;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.SchemaService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiffInfoExpressionVisitorTest {

    @Mock
    private SchemaService schemaService;
    
    @Mock
    private MappingNodeRepo nodeRepo;
    
    @Mock
    private MappingNode mockNode;
    
    private DiffInfoExpressionVisitor visitor;
    
    @Before
    public void setUp() {
        visitor = new DiffInfoExpressionVisitor(schemaService, nodeRepo);
    }
    
    @Test
    public void visit_ActionOutputPattern_ShouldResolveToNodeNameWithSuffix() {
        // Given
        String nodeId = "6883ea5a77bf050001c89896";
        String actionOutputRef = "action_output_" + nodeId + "_status";
        
        when(mockNode.getName()).thenReturn("Create External Record Action");
        when(nodeRepo.findById(nodeId)).thenReturn(Optional.of(mockNode));
        
        // When
        VariableExpression expression = new VariableExpression(actionOutputRef, false);
        visitor.visit(expression);
        
        // Then
        String result = visitor.getValue();
        assertEquals("Create External Record Action status", result);
    }
    
    @Test
    public void visit_ActionOutputPattern_NotFound_ShouldReturnOriginalValue() {
        // Given
        String nodeId = "nonexistent";
        String actionOutputRef = "action_output_" + nodeId + "_status";
        
        when(nodeRepo.findById(nodeId)).thenReturn(Optional.empty());
        
        // When
        VariableExpression expression = new VariableExpression(actionOutputRef, false);
        visitor.visit(expression);
        
        // Then
        String result = visitor.getValue();
        assertEquals(actionOutputRef, result);
    }
    
    @Test
    public void visit_OutputPattern_ShouldStillWork() {
        // Given
        String nodeId = "6883ea5a77bf050001c89896";
        String outputRef = "output_" + nodeId + ".x.typedValue";
        
        when(mockNode.getName()).thenReturn("Set Value Function");
        when(nodeRepo.findById(nodeId)).thenReturn(Optional.of(mockNode));
        
        // When
        VariableExpression expression = new VariableExpression(outputRef, false);
        visitor.visit(expression);
        
        // Then
        String result = visitor.getValue();
        assertEquals("Set Value Function", result);
    }
}