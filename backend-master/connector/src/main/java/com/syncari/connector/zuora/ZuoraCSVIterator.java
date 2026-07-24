package com.syncari.connector.zuora;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.BatchJob;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.utils.Storage;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ZuoraCSVIterator extends CSVStorageIterator {
    private final String billingPreviewRunId;
    //billing preview runs are computed on-demand when dowloaded
    long lastComputedAt = System.currentTimeMillis();


    public ZuoraCSVIterator(String billingPreviewRunId, Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader) {
        super(storage, job, pageSize, request, hasHeader);
        this.billingPreviewRunId = billingPreviewRunId;
    }

    @Override
    protected EntityData createRecord(CSVRecord next, List<String> headers) {
        EntityData record = super.createRecord(next, headers);

        // Transform external API names to Syncari API Names.
        // TODO, we need to do better here, maybe store externalApiName all the way in syncari schema?
        Map<String, AttributeSchema> syncariApiNameByExternalApiName = request.getEntitySchema().getAttributes().stream()
            .filter(x -> StringUtils.isNotEmpty(x.getDisplayName()))
            .collect(Collectors.toMap(x -> x.getDisplayName().toLowerCase(), x -> x));
        Set<String> keys = new HashSet<>(record.getValues().keySet());
        for (String key: keys) {
            if (syncariApiNameByExternalApiName.containsKey(key)) {
                record.addValue(syncariApiNameByExternalApiName.get(key).getApiName(), record.getValue(key));
            }
        }

        record.addValue("billingPreviewRunId", billingPreviewRunId);
        record.addValue("TargetDate", lastComputedAt);
        // Add all useful job details for all records.
        for (String jobDetailKey: job.getJobDetails().keySet()) {
            // These dates are not needed and causes issues with the framework's values.
            if (jobDetailKey.equalsIgnoreCase("CreatedDate") || jobDetailKey.equalsIgnoreCase("UpdatedDate")) continue;
            record.addValue(jobDetailKey, job.getJobDetails().get(jobDetailKey));
        }
        return record;
    }

    @Override
    protected String getId(Map<String, Object> values) {
        return billingPreviewRunId + "_" + values.get("account: id") + "_" + values.get("invoice item: id");
    }

    @Override
    protected Long getWatermark(Map<String, Object> values) {
        return lastComputedAt;
    }
}
