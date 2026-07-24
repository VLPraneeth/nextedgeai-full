package com.syncari.core.model;

import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class EdgeTest {

    @Test
    public void nullValueValidations() {
        Edge edge = new Edge();
        assertValidation(edge, "Edge from a node not connected to output port in Test Graph pipeline","Test Graph");
        edge.setOutput(OutputPort.any());
        assertValidation(edge, "Edge to a node not connected to input port in Test Graph pipeline","Test Graph");
        edge.setInput(InputPort.any());
        assertValidation(edge, "Edge not connected to Source node in Test Graph pipeline","Test Graph");
        MappingNode source = new MappingNode().setName("Test node");
        NodeConfiguration configuration = new TestNodeConfiguration(List.of(OutputPort.any()), List.of(InputPort.any()));

        edge.setSourceStage(source.setConfiguration(configuration));
        assertValidation(edge, "Edge not connected to Destination node in Test Graph pipeline","Test Graph");
        edge.setDestinationStage(new MappingNode().setConfiguration(configuration));
        edge.validate("test graphName");
    }


    @Ignore
    public void datatypeMismatchForOutputPort() {
        NodeConfiguration configuration = new TestNodeConfiguration(List.of(OutputPort.of(new StringType())), List.of(InputPort.any()));

        Edge edge = new Edge()
                .setOutput(OutputPort.any())
                .setInput(InputPort.any())
                .setSourceStage(new MappingNode().setConfiguration(configuration).setName("Custom Source"))
                .setDestinationStage(new MappingNode().setConfiguration(configuration));

        assertValidation(edge, "No Output port with data type object found in source stage Custom Source in graph Test Graph","Test Graph");


    }

    @Ignore
    public void datatypeMismatchForInputPort() {
        NodeConfiguration configuration = new TestNodeConfiguration(List.of(OutputPort.any()), List.of(InputPort.of(new StringType())));

        Edge edge = new Edge()
                .setOutput(OutputPort.any())
                .setInput(InputPort.any())
                .setSourceStage(new MappingNode().setConfiguration(configuration).setName("Custom Source"))
                .setDestinationStage(new MappingNode().setConfiguration(configuration).setName("Custom Destination"));

        assertValidation(edge, "No Input port with data type object found in destination stage Custom Destination in graph Test Graph","Test Graph");


    }

    private void assertValidation(Edge edge, String expectedMessage,String graphName) {
        try {
            edge.validate(graphName);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }
}

