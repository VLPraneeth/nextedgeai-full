package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.syncari.core.utils.RecordHelper.createRecord;
import static java.time.Duration.ofMinutes;
import static org.junit.Assert.assertEquals;

public class MergeValueSelectorTest {

    @Test
    public void mergStrategies(){
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("customObject1")
                .id()
                .watermark()
                .field("customField1", StringType.VALUE)
                .field("customField2", IntegerType.VALUE)
                .field("createdate", IntegerType.VALUE)
                .field("updateddate", IntegerType.VALUE)
                .getEntityDefinition();
        //oldest createdAt
        EntityData record1 = createRecord(entityDef).addValue("customField2",200).addValue("customField1","value1")
                .addValue("createdate",Instant.now().minus(ofMinutes(10l)).toEpochMilli())
                .addValue("updateddate",Instant.now().minus(ofMinutes(8l)).toEpochMilli())
                .setCreatedAt(Instant.now().minus(ofMinutes(10l)).toEpochMilli())
                .setLastModified(Instant.now().minus(ofMinutes(8l)).toEpochMilli());

        //highest cusstomfield2
        //latest lastmodifed
        EntityData record2 = createRecord(entityDef).addValue("customField2",300).addValue("customField1","value2")
                .addValue("createdate",Instant.now().minus(ofMinutes(7l)).toEpochMilli())
                .addValue("updateddate",Instant.now().toEpochMilli())
                .setCreatedAt(Instant.now().minus(ofMinutes(7l)).toEpochMilli())
                .setLastModified(Instant.now().toEpochMilli());
        //oldest lastmodified
        EntityData record3 = createRecord(entityDef).addValue("customField2",200).addValue("customField1","value3")
                .addValue("createdate",Instant.now().minus(ofMinutes(10l)).toEpochMilli())
                .addValue("updateddate",Instant.now().minus(ofMinutes(9l)).toEpochMilli())
                .setCreatedAt(Instant.now().minus(ofMinutes(10l)).toEpochMilli())
                .setLastModified(Instant.now().minus(ofMinutes(9l)).toEpochMilli());
        //lowest customfield2
        //latest CreatedAt
        EntityData record4 = createRecord(entityDef).addValue("customField2",4).addValue("customField1","value4")
                .addValue("createdate",Instant.now().minus(ofMinutes(1l)).toEpochMilli())
                .addValue("updateddate",Instant.now().minus(ofMinutes(8l)).toEpochMilli())
                .setCreatedAt(Instant.now().minus(ofMinutes(1l)).toEpochMilli())
                .setLastModified(Instant.now().minus(ofMinutes(8l)).toEpochMilli());

        MergeValueSelector mergeValueSelector = new MergeValueSelector(List.of(record1, record2, record3, record4), entityDef);
        String fieldId = entityDef.getFieldByName("customField2").getId();
        String fieldId1 = entityDef.getFieldByName("customField1").getId();
        assertEquals(300, ((Map)mergeValueSelector.highestValue(fieldId)).get("result"));
        assertEquals(4, ((Map)mergeValueSelector.lowestValue(fieldId)).get("result"));
        assertEquals(4, ((Map)mergeValueSelector.latestCreatedWithValue(fieldId, Map.of())).get("result"));
        assertEquals(200, ((Map)mergeValueSelector.oldestCreatedWithValue(fieldId, Map.of())).get("result"));
        assertEquals(300, ((Map)mergeValueSelector.latestUpdatedWithValue(fieldId, Map.of())).get("result"));
        assertEquals(200, ((Map)mergeValueSelector.oldestUpdatedWithValue(fieldId, Map.of())).get("result"));
        assertEquals(704.0, ((Map)mergeValueSelector.sum(fieldId)).get("result"));
        assertEquals(999l, ((Map)mergeValueSelector.setValue(fieldId,"999")).get("result"));
        assertEquals("200,300,200,4", ((Map)mergeValueSelector.concat(fieldId,",")).get("result"));
        assertEquals("2003002004", ((Map)mergeValueSelector.concat(fieldId,null)).get("result"));
        assertEquals("value2", ((Map)mergeValueSelector.firstMatchingValue(fieldId1,List.of("value2","value1"))).get("result"));
        assertEquals("value1", ((Map)mergeValueSelector.firstMatchingValue(fieldId1,List.of("value100","value1","value3"))).get("result"));

        // Test firstMatchingValueIgnoreCase - case-insensitive matching
        assertEquals("value2", ((Map)mergeValueSelector.firstMatchingValueIgnoreCase(fieldId1,List.of("VALUE2","VALUE1"))).get("result"));
        assertEquals("value1", ((Map)mergeValueSelector.firstMatchingValueIgnoreCase(fieldId1,List.of("VALUE100","VALUE1","VALUE3"))).get("result"));
        assertEquals("value4", ((Map)mergeValueSelector.firstMatchingValueIgnoreCase(fieldId1,List.of("Value4","Value3"))).get("result"));
        // Test with mixed case in both values and match list
        assertEquals("value1", ((Map)mergeValueSelector.firstMatchingValueIgnoreCase(fieldId1,List.of("VaLuE1","vAlUe2"))).get("result"));

        assertEquals(200l,mergeValueSelector.mostFrequentValue(fieldId));
        assertEquals(300l,mergeValueSelector.leastFrequentValue(fieldId));

        String createdAtFieldId = entityDef.getFieldByName("createdate").getId();
        String lastModifiedAtFieldId = entityDef.getFieldByName("updateddate").getId();
        assertEquals("value3", ((Map)mergeValueSelector.firstNotMatchingValue(fieldId1,
                Map.of("multivaluetext", List.of("value2","value1"),"sortField",createdAtFieldId,"sortDirection","ascending"))).get("result"));
        assertEquals("value4", ((Map)mergeValueSelector.firstNotMatchingValue(fieldId1,
                Map.of("multivaluetext", List.of("value2","value1"),"sortField",createdAtFieldId,"sortDirection","descending"))).get("result"));
        assertEquals("value3", ((Map)mergeValueSelector.firstNotMatchingValue(fieldId1,
                Map.of("multivaluetext", List.of("value2","value1"),"sortField",lastModifiedAtFieldId,"sortDirection","ascending"))).get("result"));
        assertEquals("value4", ((Map)mergeValueSelector.firstNotMatchingValue(fieldId1,
                Map.of("multivaluetext", List.of("value2","value1"),"sortField",lastModifiedAtFieldId,"sortDirection","descending"))).get("result"));

        // Test firstNotMatchingValueIgnoreCase - case-insensitive non-matching
        assertEquals("value3", ((Map)mergeValueSelector.firstNotMatchingValueIgnoreCase(fieldId1,
                Map.of("multivaluetext", List.of("VALUE2","VALUE1"),"sortField",createdAtFieldId,"sortDirection","ascending"))).get("result"));
        assertEquals("value4", ((Map)mergeValueSelector.firstNotMatchingValueIgnoreCase(fieldId1,
                Map.of("multivaluetext", List.of("VALUE2","VALUE1"),"sortField",createdAtFieldId,"sortDirection","descending"))).get("result"));
        assertEquals("value3", ((Map)mergeValueSelector.firstNotMatchingValueIgnoreCase(fieldId1,
                Map.of("multivaluetext", List.of("VaLuE2","vAlUe1"),"sortField",lastModifiedAtFieldId,"sortDirection","ascending"))).get("result"));
        assertEquals("value4", ((Map)mergeValueSelector.firstNotMatchingValueIgnoreCase(fieldId1,
                Map.of("multivaluetext", List.of("value2","value1"),"sortField",lastModifiedAtFieldId,"sortDirection","descending"))).get("result"));
        // Test with mixed case - should exclude value3 when "Value3" is in the exclude list
        assertEquals("value4", ((Map)mergeValueSelector.firstNotMatchingValueIgnoreCase(fieldId1,
                Map.of("multivaluetext", List.of("value2","value1","Value3"),"sortField",createdAtFieldId,"sortDirection","descending"))).get("result"));
    }

}