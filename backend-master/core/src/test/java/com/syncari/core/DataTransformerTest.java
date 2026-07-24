package com.syncari.core;

import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import com.syncari.core.datatype.ChildType;
import com.syncari.core.model.Connector;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.SchemaService;
import com.syncari.core.utils.SchemaHelper;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataTransformerTest {
    @Test
    public void childSchemaSetCorrectly(){
        final DataTransformer dataTransformer = new DataTransformer();
        dataTransformer.schemaService = mock(SchemaService.class);

        final Connector connector = new Connector(ObjectId.get().toString());
        connector.setMetadata(new ConnectorMetadata());
        EntityDefinition entityDefinition = SchemaHelper.createEntityDefinition("customEntity", connector)
                .id().string("firstName").field(
                        "child", ChildType.VALUE
                ).getEntityDefinition();
        entityDefinition.getFieldByName("child").setReferenceTo("customChildEntity");
        EntityDefinition childSchema = SchemaHelper.createEntityDefinition("customChildEntity", connector)
                .id().string("childName1").getEntityDefinition();

        when(dataTransformer.schemaService.getEntity(connector.getId(),"customChildEntity")).thenReturn(childSchema);
        when(dataTransformer.schemaService.findEntity(connector.getId(),"customChildEntity")).thenReturn(Optional.of(childSchema));
        final AttributeSchema child = dataTransformer.toAttrSchema(entityDefinition.getFieldByName("child"), entityDefinition, connector);
        assertEquals(childSchema.getApiName(),child.getChildSchema().getApiName());
        assertEquals(childSchema.getId(),child.getChildSchema().getId());

        final EntitySchema entitySchema = dataTransformer.toEntitySchema(entityDefinition, connector);
        final AttributeSchema child1 = entitySchema.getField("child").get();
        assertEquals(childSchema.getApiName(),child1.getChildSchema().getApiName());
        assertEquals(childSchema.getId(),child1.getChildSchema().getId());
        assertEquals("customChildEntity",child1.getReferenceTo());
        assertEquals("customChildEntity",child1.getReferenceTo());
        assertEquals("child", child1.getDataType());



    }
}