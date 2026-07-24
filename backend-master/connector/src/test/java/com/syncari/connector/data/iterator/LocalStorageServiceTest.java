package com.syncari.connector.data.iterator;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.database.HsqlService;
import org.apache.commons.collections4.ListUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

public class LocalStorageServiceTest {
    @Test
    public void getByWMUsesOffsets() throws Exception {
        LocalStorageService localStorageService = new LocalStorageService();
        ConnectorInfo c = new ConnectorInfo();
        localStorageService.dbService = new HsqlService();
        final SyncRequest request = new SyncRequest();
        request.setPageSize(5);
        request.setConnector(c);
        final long l = System.currentTimeMillis();
        WatermarkInfo wm = new WatermarkInfo(0l, l, true, 0);
        request.setWatermark(wm);
        final EntitySchema testDB = new EntitySchema("testDB");
        testDB.addField(new AttributeSchema("id", "string").setIdField(true));
        testDB.addField(new AttributeSchema("f1", "string"));
        testDB.addField(new AttributeSchema("f2", "string"));
        testDB.addField(new AttributeSchema("f3", "string"));
        testDB.addField(new AttributeSchema("f4", "string"));
        testDB.addField(new AttributeSchema("wmField", "datetime").setWatermarkField(true));
        request.setEntitySchema(testDB);
        localStorageService.provisionIfNotExists(request, "testDB");
        try {
            List<EntityData> records = createRecords(testDB, 7, l - 1000000l, () -> 0);
            records.addAll(createRecords(testDB, 12, l - 1000000l, () -> 1));
            final List<List<EntityData>> partitions = ListUtils.partition(records, 5);
            final Iterator<List<EntityData>> iterator = partitions.iterator();
            localStorageService.fetch(request, new AbstractEntityDataBatchIterator() {

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public List<EntityData> next() {
                    return iterator.next();
                }
            });
            final FetchResponse byWatermark = localStorageService.getByWatermark(request);
            final EntityDataBatchIterator iterator1 = byWatermark.getIterator();
            List<EntityData> fetched = new ArrayList<>();
            while (iterator1.hasNext()) {
                fetched.addAll(iterator1.next());
            }
            assertEquals(records.size(), fetched.size());
            assertEquals(19l, iterator1.getLastOffset());
        } finally {
            localStorageService.cleanupDB(request);
        }
    }

    private List<EntityData> createRecords(EntitySchema schema, int numRecords, long startWM, Supplier<Integer> wmIncrement) {
        List<EntityData> records = new ArrayList<>();
        long wm = startWM;
        do {
            records.add(new EntityData(schema.getApiName())
                    .setId(UUID.randomUUID().toString())
                    .setLastModified(wm));
            wm = wm + wmIncrement.get();
        } while (--numRecords > 0);
        return records;
    }
}