package com.syncari.core.model;

import com.syncari.core.datatype.IntegerType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.util.Scope;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;

import static com.syncari.core.utils.GraphHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MappingNodeTest {

    @Test
    public void validateAttributeSinkNode(){
        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta"));
        var sinkField1 = SchemaHelper.createAttribute("sinkfield1", IntegerType.VALUE, sinkEntity.getId());
        sinkEntity.addField(sinkField1);
        MappingNode node = new MappingNode().setName("attrSinkNode").setScope(Scope.ATTRIBUTE).setApiName(sinkField1.getApiName())
                .setMappingGraphId("graph123");
        node.setId(ObjectId.get().toHexString());

        // case 1: null attribute definition
        AttributeSinkNodeConfig sinkNodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(null);
        node.setConfiguration(sinkNodeConfig);

        try{
            node.validate("graph123");
            fail();
        } catch (SyncariValidationException e){
            assertEquals("A destination attribute is required in graph123 pipeline, node attrSinkNode", e.getMessage());
        }

        // case 2: Inconvertible data type
        sinkNodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(sinkField1).setDefaultValue("Value");
        node.setConfiguration(sinkNodeConfig);
        try{
            node.validate("graph123");
            fail();
        } catch (SyncariValidationException e){
            assertEquals("The value 'Value' in node 'attrSinkNode' of pipeline 'graph123' must be of type 'integer' or a token", e.getMessage());
        }

        // case 4: default value with convertible data type
        sinkNodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(sinkField1).setDefaultValue("123");
        node.setConfiguration(sinkNodeConfig);
        node.validate("graph123");

        // case 4: null default value
        sinkNodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(sinkField1).setDefaultValue(null);
        node.setConfiguration(sinkNodeConfig);
        node.validate("graph123");

        // case 5: empty default value
        sinkNodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(sinkField1).setDefaultValue("");
        node.setConfiguration(sinkNodeConfig);
        node.validate("graph123");

        // case 5: default value as token
        sinkNodeConfig = new AttributeSinkNodeConfig().setAttributeDefinition(sinkField1).setDefaultValue("{{custom.token}}");
        node.setConfiguration(sinkNodeConfig);
        node.validate("graph123");
    }
}
