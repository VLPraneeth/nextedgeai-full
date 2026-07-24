package com.syncari.restutils.transformers;

import com.syncari.connector.EntityData;
import com.syncari.restutils.data.EntityRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RestObjectTransformer {

    private static final String DFI = "dfi";
    private static final String SYNCARI_ID = "syncariId";
    private static final String LAST_MODIFIED = "lastModified";
    private static final String SYNCARI_TIMESTAMP = "syncariTimestamp";

    public List<EntityRecord> toEntityRecords(List<EntityData> data) {
        return data.stream().map(d -> toEntityRecord(d)).collect(Collectors.toList());
    }

    public EntityRecord toEntityRecord(EntityData d) {
        EntityRecord record = new EntityRecord().setDeleted(d.isDeleted()).setSyncariId(d.getId())
                .setSyncariTimestamp(d.getSyncariTimestamp()).setValues(d.getValues()).setLastModified(d.getLastModified());
        record.getValues().put(SYNCARI_TIMESTAMP, d.getSyncariTimestamp());
        record.getValues().put(LAST_MODIFIED, d.getLastModified());
        record.getValues().put(SYNCARI_ID, d.getId());
        record.getValues().put(DFI, d.getSyncariScore() == null ? 0 : d.getSyncariScore().getRecordScore());
        return record;
    }

}
