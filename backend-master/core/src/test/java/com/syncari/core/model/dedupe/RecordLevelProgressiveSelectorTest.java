package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RecordLevelProgressiveSelectorTest {



    @Test
    public void mostCompletedRecordTestNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12).addValue("salary",5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12).addValue("salary",5000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,recordLevelProgressiveSelector.mostCompleteRecord(candidates).size());
    }

    @Test
    public void oldestCreatedRecordTestNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(3,recordLevelProgressiveSelector.oldestCreatedRecord(candidates).size());
    }

    @Test
    public void oldestUpdatedRecordTestOneWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(1,recordLevelProgressiveSelector.oldestUpdatedRecord(candidates).size());
        assertEquals(candidate2.getSyncariEntityId(),recordLevelProgressiveSelector.oldestUpdatedRecord(candidates).get(0).getSyncariEntityId());
    }

    @Test
    public void oldestUpdatedRecordTestNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(80).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,recordLevelProgressiveSelector.oldestUpdatedRecord(candidates).size());
    }

    @Test
    public void latestUpdatedRecordTestOneWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(1,recordLevelProgressiveSelector.latestUpdatedRecord(candidates).size());
        assertEquals(candidate1.getSyncariEntityId(),recordLevelProgressiveSelector.latestUpdatedRecord(candidates).get(0).getSyncariEntityId());
    }

    @Test
    public void latestUpdatedRecordTestNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(100).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,recordLevelProgressiveSelector.latestUpdatedRecord(candidates).size());
    }

    @Test
    public void latestCreatedRecordTestNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(100).setCreatedAt(12);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(12);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        RecordLevelProgressiveSelector recordLevelProgressiveSelector = new RecordLevelProgressiveSelector(candidates, entityDef);
        assertEquals(3,recordLevelProgressiveSelector.latestCreatedRecord(candidates).size());
    }
}
