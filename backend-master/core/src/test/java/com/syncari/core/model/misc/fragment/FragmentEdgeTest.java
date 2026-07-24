package com.syncari.core.model.misc.fragment;

import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.MapType;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import org.junit.Test;

import static org.junit.Assert.*;

public class FragmentEdgeTest {

    @Test
    public void validate(){

        FragmentEdge edge = new FragmentEdge().setTemplateId("edge1");
        try{
            edge.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Edge in Fragment is not connected to valid Source node", e.getMessage());
        }

        FragmentNode node1 = new FragmentNode().setTemplateId("node1");
        node1.setName("node1");
        edge.setSourceStage(node1);
        try{
            edge.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Edge in Fragment is not connected to valid Destination node", e.getMessage());
        }
        FragmentNode node2 = new FragmentNode().setTemplateId("node2");
        node2.setName("node2");
        edge.setDestinationStage(node2);
        try{
            edge.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Edge from node1 is not connected to valid output port", e.getMessage());
        }
        edge.setOutput(OutputPort.any());
        try{
            edge.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Edge from node2 is not connected to valid input port", e.getMessage());
        }
        edge.setInput(InputPort.any());

        edge.validate();

    }

    @Test
    public void validate_CyclicReference(){
        FragmentNode node1 = new FragmentNode().setTemplateId("node1");
        node1.setName("node1");
        FragmentEdge edge = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node1)
                .setSourceStage(node1).setOutput(OutputPort.any()).setInput(InputPort.any());
        try{
            edge.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Edge in Fragment is creating cyclic reference on node node1", e.getMessage());
        }
    }

    @Test
    public void validate_DatatypeMismatch(){
        FragmentNode node1 = new FragmentNode().setTemplateId("node1");
        node1.setName("node1");
        FragmentNode node2 = new FragmentNode().setTemplateId("node2");
        node1.setName("node2");
        FragmentEdge edge = new FragmentEdge().setTemplateId("edge1").setDestinationStage(node2)
                .setSourceStage(node1).setOutput(OutputPort.of(new DateType())).setInput(InputPort.of(new MapType()));
        try{
            edge.validate();
            fail();
        } catch (RuntimeException e){
            assertEquals("Input data type complex does not match Output Datatype date for edge between node2 and null in fragment", e.getMessage());
        }
    }
}
