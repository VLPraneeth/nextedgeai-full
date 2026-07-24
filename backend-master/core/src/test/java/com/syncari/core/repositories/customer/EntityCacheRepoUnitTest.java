package com.syncari.core.repositories.customer;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.util.HashMap;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EntityCacheRepoUnitTest {

    @Test
    public void updateValues() {
        final EntityCacheRepo entityCacheRepo = new EntityCacheRepo(null, null);
        SchemaHelper accountDef = SchemaHelper.createEntityDefinition("act_test")
                .id();
        for (int i = 0; i < 100; i++) {
            accountDef = accountDef.string("field" + i);
        }
        accountDef.field("dtfield", DateType.VALUE);
        accountDef.field("boolfield", BooleanType.VALUE);
        accountDef.field("numField", IntegerType.VALUE);
        accountDef.field("dblField", DoubleType.VALUE);
        accountDef.datetime("dttimefield");
        EntityDefinition entityDefinition = accountDef.getEntityDefinition();

        long currentTime = System.currentTimeMillis();
        final EntityData update = new EntityData();
        update.addValue("field0", null);
        update.addValue("field2", "field2Value");
        update.addValue("field5", "field5Value");
        update.setSyncariTimestamp(currentTime);
        update.setLastModified(currentTime);
        final HashMap existingCacheRecord = new HashMap();
        existingCacheRecord.put("field0", "field0Value");
        existingCacheRecord.put("field1", "field1Value");

        existingCacheRecord.put("__nf", "field3,field4,field5");
        //new nullfield added to existing nullfields, new nonnull fields removed
        entityCacheRepo.updateCacheRecord(entityDefinition, update, existingCacheRecord);
        final Set<String> nullFields = Set.of(((String) existingCacheRecord.get("__nf")).split(","));
        assertEquals(Set.of("field0", "field3", "field4"), nullFields);
        assertEquals("field2Value", existingCacheRecord.get("field2"));
        assertEquals("field5Value", existingCacheRecord.get("field5"));
        //value as well is removed from record
        assertNull(existingCacheRecord.get("field0"));
        assertEquals(currentTime, existingCacheRecord.get("syncariTimestamp"));
        assertEquals(currentTime, existingCacheRecord.get("lastModified"));

        //__nf removed if there are none after update
        update.addValue("field0", "field0Value");
        update.addValue("field3", "field3Value");
        update.addValue("field4", "field4Value");
        update.addValue("field2", "field2UpdatedValue");
        update.setSyncariTimestamp(currentTime);
        update.setLastModified(currentTime);

        entityCacheRepo.updateCacheRecord(entityDefinition, update, existingCacheRecord);
        assertNull(existingCacheRecord.get("__nf"));
        //newly added values present,
        assertEquals("field0Value", existingCacheRecord.get("field0"));
        assertEquals("field3Value", existingCacheRecord.get("field3"));
        assertEquals("field4Value", existingCacheRecord.get("field4"));
        //unchanged value is unmodified
        assertEquals("field5Value", existingCacheRecord.get("field5"));
        //updated value reflected
        assertEquals("field2UpdatedValue", existingCacheRecord.get("field2"));

        assertEquals(currentTime, existingCacheRecord.get("syncariTimestamp"));
        assertEquals(currentTime, existingCacheRecord.get("lastModified"));
    }
}
