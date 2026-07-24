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

public class FieldLevelProgressiveSelectionTest {

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
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.firstMatchingValue(city.getId(),candidates, List.of("NJ","SJ","SFO")).get(0).getSyncariEntityId());
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector2 = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector2.firstMatchingValue(city.getId(),candidates, List.of("SJ","NJ","SFO")).get(0).getSyncariEntityId());

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
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(0,fieldLevelProgressiveSelector.firstMatchingValue(city.getId(),candidates, List.of("NJ","SJ","SFO")).size());
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
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.firstMatchingValue(city.getId(),candidates, List.of("NJ","SJ","Newark")).get(0).getSyncariEntityId());
        assertNotEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.firstMatchingValue(city.getId(),candidates, List.of("NJ","SJ","Newark")).get(0).getSyncariEntityId());
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
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.highestValue(city.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.highestValue("field_"+city.getId(),candidates));
    }


    @Test
    public void highestValueNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 5000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 5000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(3,fieldLevelProgressiveSelector.highestValue(salary.getId(),candidates).size());
    }

    @Test
    public void highestValueWinner_ignores_empty(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        AttributeDefinition state = SchemaHelper.createAttribute("state", StringType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        entityDef.addField(state);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 8000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertNotEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.highestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.highestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        candidate1.addValue("state","");
        candidate2.addValue("state","");
        candidate3.addValue("state","");
        assertEquals(0,fieldLevelProgressiveSelector.highestValue(state.getId(),candidates).size());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.highestValue("field_"+salary.getId(),candidates));
        assertEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.highestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
    }
    @Test
    public void lowestValueNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition city = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(city);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 5000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 5000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(city.getId(),candidates).get(0).getSyncariEntityId());
        assertEquals(3,fieldLevelProgressiveSelector.lowestValue(city.getId(),candidates).size());
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
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(city.getId(),candidates).get(0).getSyncariEntityId());
        assertEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(city.getId(),candidates).get(0).getSyncariEntityId());
    }

    @Test
    public void lowestValueWinner_ignores_empty(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        AttributeDefinition state = SchemaHelper.createAttribute("state", StringType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        entityDef.addField(state);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(1).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(1).addValue("salary", 8000);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(1).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        candidate1.addValue("state","");
        candidate2.addValue("state","");
        candidate3.addValue("state","");
        assertEquals(0,fieldLevelProgressiveSelector.lowestValue(state.getId(),candidates).size());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue("field_"+salary.getId(),candidates).get(0).getSyncariEntityId());
        assertEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.lowestValue(salary.getId(),candidates).get(0).getSyncariEntityId());
    }
    @Test
    public void oldestUpdatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertNotEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.oldestUpdatedWithValue("field_"+salary.getId(),candidates).get(0).getSyncariEntityId());
        //even though this the oldest, it has no value for salary field

        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.oldestUpdatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.oldestUpdatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());

    }
    @Test
    public void oldestUpdatedWithValueNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(50).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(50).addValue("salary", 5000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,fieldLevelProgressiveSelector.oldestUpdatedWithValue(salary.getId(),candidates).size());
    }



    @Test
    public void oldestCreatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(10);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(20).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.oldestCreatedWithValue("field_"+salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.oldestCreatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.oldestCreatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
    }
    @Test
    public void oldestCreatedWithValueNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(20).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(10);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(20).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,fieldLevelProgressiveSelector.oldestCreatedWithValue("field_"+salary.getId(),candidates).size());
    }

    @Test
    public void latestUpdatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.latestUpdatedWithValue("field_"+salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.latestUpdatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.latestUpdatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
    }
    @Test
    public void latestUpdatedWithValueNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(50).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(50);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(50).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,fieldLevelProgressiveSelector.latestUpdatedWithValue("field_"+salary.getId(),candidates).size());
    }
    @Test
    public void latestCreatedWithValue(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(12).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(10);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(20).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertNotEquals(candidate1.getSyncariEntityId(),fieldLevelProgressiveSelector.latestCreatedWithValue("field_"+salary.getId(),candidates).get(0).getSyncariEntityId());
        assertNotEquals(candidate2.getSyncariEntityId(),fieldLevelProgressiveSelector.latestCreatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
        assertEquals(candidate3.getSyncariEntityId(),fieldLevelProgressiveSelector.latestCreatedWithValue(salary.getId(),candidates).get(0).getSyncariEntityId());
    }
    @Test
    public void latestCreatedWithValueNoWinner(){
        EntityDefinition entityDef = SchemaHelper.createEntityDef("testentity", "test", null);
        entityDef.addField(SchemaHelper.createAttribute("idField", StringType.VALUE, entityDef.getId()).setIdField(true));
        AttributeDefinition salary = SchemaHelper.createAttribute("salary", IntegerType.VALUE, entityDef.getId());
        entityDef.addField(salary);
        EntityData candidate1 = new EntityData("testentity").setSyncariEntityId("record1").setId("record1").setLastModified(100).setCreatedAt(20).addValue("salary", 5000);
        EntityData candidate2 = new EntityData("testentity").setSyncariEntityId("record2").setId("record2").setLastModified(10);
        EntityData candidate3 = new EntityData("testentity").setSyncariEntityId("record3").setId("record3").setLastModified(80).setCreatedAt(20).addValue("salary", 6000);
        List<EntityData> candidates = List.of(candidate1,candidate2,candidate3);
        FieldLevelProgressiveSelector fieldLevelProgressiveSelector = new FieldLevelProgressiveSelector(candidates, entityDef);
        assertEquals(2,fieldLevelProgressiveSelector.latestCreatedWithValue(salary.getId(),candidates).size());
    }
}
