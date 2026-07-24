package com.syncari.core.sync;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.AttributeDefinition;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EntitySourceHelperTest {

    @Test
    public void convertMultivalued() {
        EntitySourceHelper entitySourceHelper = new EntitySourceHelper();
        EntityData entityData1 = entitySourceHelper.fixDatatypes(Map.of("numericfield1", new AttributeDefinition()
                .setApiName("numericField1")
                .setDataType(IntegerType.VALUE)
                .setMultiValueField(true)), new EntityData().addValue("numericField1", List.of("1", "2", "3")));
        assertEquals(new ArrayList(List.of(1l, 2l, 3l)), entityData1.getValue("numericField1"));

        EntityData entityData2 = entitySourceHelper.fixDatatypes(Map.of("datetimefield", new AttributeDefinition()
                .setApiName("datetimeField")
                .setDataType(DatetimeType.VALUE)
                .setMultiValueField(true)), new EntityData().addValue("datetimeField", List.of("2020-01-01T00:00:35Z", "2020-02-01T00:00:35Z", "2020-01-01T12:10:35Z")));
        List<ZonedDateTime> expected = new ArrayList(List.of(ZonedDateTime.parse("2020-01-01T00:00:35Z"),
                ZonedDateTime.parse("2020-02-01T00:00:35Z"), ZonedDateTime.parse("2020-01-01T12:10:35Z")));
        assertEquals(expected, entityData2.getValue("datetimeField"));


        EntityData entityData3 = entitySourceHelper.fixDatatypes(Map.of("numericfield1", new AttributeDefinition()
                .setApiName("numericField1")
                .setDataType(IntegerType.VALUE)
                .setMultiValueField(true)), new EntityData().addValue("numericField1", 1));
        assertEquals(new ArrayList(List.of(1l)), entityData3.getValue("numericField1"));

        EntityData entityData4 = entitySourceHelper.fixDatatypes(Map.of("numericfield1", new AttributeDefinition()
                .setApiName("numericField1")
                .setDataType(IntegerType.VALUE)),
                new EntityData().addValue("numericField1", 1));
        assertEquals(1l, entityData4.getValue("numericField1"));
    }

    @Test
    public void fixDataTypes_KeyCasing() {
        String mixedCasedAttribApiName = "MiXedCaseKey";
        String mixedCasedResponseKey = "miXedCaseKey"; // Note the first character is different from attribute apiName.
        EntitySourceHelper entitySourceHelper = new EntitySourceHelper();
        EntityData entityData1 = entitySourceHelper.fixDatatypes(Map.of("mixedcasekey", new AttributeDefinition()
                .setApiName(mixedCasedAttribApiName)
                .setDataType(IntegerType.VALUE)
                .setMultiValueField(true)), new EntityData().addValue(mixedCasedResponseKey, List.of("1", "2", "3")));
        assertEquals(new ArrayList(List.of(1l, 2l, 3l)), entityData1.getValue("MiXedCaseKey"));
    }

}