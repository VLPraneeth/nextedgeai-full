package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FieldLevelSelectorTest {

    @Test
    public void firstMatchingValueWithAllMatchingRecord(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("city", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setLastModified(1).addValue("city", "SFO");
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setLastModified(1).addValue("city", "SJ");
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setLastModified(1).addValue("city", "NJ");
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate1, List.of("NJ","SJ","SFO")));
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate2, List.of("NJ","SJ","SFO")));
        assertTrue(fieldLevelSelector.firstMatchingValue(city.getId(),candidate3, List.of("NJ","SJ","SFO")));
        FieldLevelSelector fieldLevelSelector2 = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector2.firstMatchingValue(city.getId(),candidate1, List.of("SJ","NJ","SFO")));
        assertTrue(fieldLevelSelector2.firstMatchingValue(city.getId(),candidate2, List.of("SJ","NJ","SFO")));
        assertFalse(fieldLevelSelector2.firstMatchingValue(city.getId(),candidate3, List.of("SJ","NJ","SFO")));
    }

    @Test
    public void firstMatchingValueWithNoMatchingRecord(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("city", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setLastModified(1).addValue("city", "Fremont");
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setLastModified(1).addValue("city", "Newark");
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setLastModified(1).addValue("city", "Palo alto");
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate1, List.of("NJ","SJ","SFO")));
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate2, List.of("NJ","SJ","SFO")));
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate3, List.of("NJ","SJ","SFO")));
    }

    @Test
    public void firstMatchingValueWithsSecondValueMatch(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("city", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setLastModified(1).addValue("city", "Fremont");
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setLastModified(1).addValue("city", "Newark");
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setLastModified(1).addValue("city", "Palo alto");
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate1, List.of("NJ","SJ","Newark")));
        assertTrue(fieldLevelSelector.firstMatchingValue(city.getId(),candidate2, List.of("NJ","SJ","Newark")));
        assertFalse(fieldLevelSelector.firstMatchingValue(city.getId(),candidate3, List.of("NJ","SJ","Newark")));
    }

    @Test
    public void highestValueWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 8000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.highestValue(city.getId(),candidate1));
        assertTrue(fieldLevelSelector.highestValue(city.getId(),candidate2));
        assertFalse(fieldLevelSelector.highestValue("field_"+city.getId(),candidate3));
    }
    @Test
    public void highestValueWinner_ignores_empty(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        AttributeDefinition state = SchemaHelper.createAttribute("state", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        entityDef.addField(state);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 8000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.highestValue(state.getId(),candidate1));
        assertFalse(fieldLevelSelector.highestValue(state.getId(),candidate2));
        assertFalse(fieldLevelSelector.highestValue(state.getId(),candidate3));
        candidate1.addValue("state","");
        candidate2.addValue("state","");
        candidate3.addValue("state","");
        assertFalse(fieldLevelSelector.highestValue(state.getId(),candidate1));
        assertFalse(fieldLevelSelector.highestValue(state.getId(),candidate2));
        assertFalse(fieldLevelSelector.highestValue(state.getId(),candidate3));

        assertTrue(fieldLevelSelector.highestValue(city.getId(),candidate2));

        assertFalse(fieldLevelSelector.highestValue("field_"+city.getId(),candidate3));
    }
    @Test
    public void lowestValueWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 8000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertTrue(fieldLevelSelector.lowestValue(city.getId(),candidate1));
        assertFalse(fieldLevelSelector.lowestValue(city.getId(),candidate2));
        assertFalse(fieldLevelSelector.lowestValue("field_"+city.getId(),candidate3));
    }
    @Test
    public void lowestValueWinner_ignores_empty(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        AttributeDefinition state = SchemaHelper.createAttribute("state", StringType.VALUE, entityDef.getId());
        entityDef.addField(city);
        entityDef.addField(state);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 8000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.lowestValue(state.getId(),candidate1));
        assertFalse(fieldLevelSelector.lowestValue(state.getId(),candidate2));
        assertFalse(fieldLevelSelector.lowestValue(state.getId(),candidate3));
        candidate1.addValue("state","");
        candidate2.addValue("state","");
        candidate3.addValue("state","");
        assertFalse(fieldLevelSelector.lowestValue(state.getId(),candidate1));
        assertFalse(fieldLevelSelector.lowestValue(state.getId(),candidate2));
        assertFalse(fieldLevelSelector.lowestValue(state.getId(),candidate3));
        assertTrue(fieldLevelSelector.lowestValue(city.getId(),candidate1));
        assertFalse(fieldLevelSelector.lowestValue(city.getId(),candidate2));
        assertFalse(fieldLevelSelector.lowestValue("field_"+city.getId(),candidate3));
    }
    @Test
    public void oldestUpdatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.oldestUpdatedWithValue("field_"+city.getId(),candidate1));
        //even though this the oldest, it has no value for salary field
        assertFalse(fieldLevelSelector.oldestUpdatedWithValue(city.getId(),candidate2));
        assertTrue(fieldLevelSelector.oldestUpdatedWithValue(city.getId(),candidate3));
    }

    @Test
    public void oldestCreatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(10);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(20).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertTrue(fieldLevelSelector.oldestCreatedWithValue("field_"+city.getId(),candidate1));
        //even though this the oldest, it has no value for salary field
        assertFalse(fieldLevelSelector.oldestCreatedWithValue(city.getId(),candidate2));
        assertFalse(fieldLevelSelector.oldestCreatedWithValue(city.getId(),candidate3));
    }

    @Test
    public void latestUpdatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertTrue(fieldLevelSelector.latestUpdatedWithValue("field_"+city.getId(),candidate1));
        //even though this the oldest, it has no value for salary field
        assertFalse(fieldLevelSelector.latestUpdatedWithValue(city.getId(),candidate2));
        assertFalse(fieldLevelSelector.latestUpdatedWithValue(city.getId(),candidate3));
    }

    @Test
    public void latestCreatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(10);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(20).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(candidates, entityDef);
        assertFalse(fieldLevelSelector.latestCreatedWithValue("field_"+city.getId(),candidate1));
        //even though this the oldest, it has no value for salary field
        assertFalse(fieldLevelSelector.latestCreatedWithValue(city.getId(),candidate2));
        assertTrue(fieldLevelSelector.latestCreatedWithValue(city.getId(),candidate3));
    }
}