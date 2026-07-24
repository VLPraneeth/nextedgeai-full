package com.syncari.connector.amplitude;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.utils.Storage;
import org.apache.commons.csv.CSVRecord;

import java.util.List;
import java.util.Map;

public class CohortCSVIterator extends CSVStorageIterator {
    private final String cohortId;
    private final String cohortName;
    //cohorts are computed on-demand when dowloaded
    long lastComputedAt = System.currentTimeMillis();


    public CohortCSVIterator(String cohortId, String cohortName, Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader) {
        super(storage, job, pageSize, request, hasHeader);
        this.cohortId = cohortId;
        this.cohortName = cohortName;
    }

    @Override
    protected EntityData createRecord(CSVRecord next, List<String> headers) {
        EntityData record = super.createRecord(next, headers);
        record.addValue("cohort_id", cohortId);
        record.addValue("cohort_name", cohortName);
        record.addValue("lastComputed", lastComputedAt);
        return record;
    }

    @Override
    protected String getId(Map<String, Object> values) {
        return cohortId+"_"+values.get("amplitude_id");
    }

    @Override
    protected Long getWatermark(Map<String, Object> values) {
        return lastComputedAt;
    }
}
