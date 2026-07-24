package com.syncari.viper.simulation;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.DataService;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class ReadOnlyDataService implements DataService {

    private DataService delegate;
    private Set<SyncRequest> creates = new HashSet<>();
    private Set<SyncRequest> updates = new HashSet<>();
    private Set<SyncRequest> deletes = new HashSet<>();

    public ReadOnlyDataService(DataService delegate) {
        this.delegate = delegate;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return delegate.getByWatermark(request);
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return delegate.getFirstCreatedTime(request);
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        return delegate.getByIds(request);
    }

    @Override
    public FetchResponse getDeletedByWatermark(SyncRequest request) {
        return null;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        creates.add(request);
        return new SyncResponse(true);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        updates.add(request);
        return new SyncResponse(true);
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        deletes.add(request);
        return new SyncResponse(true);
    }

    @Override
    public MergeResponse merge(MergeRequest request) {
        return new MergeResponse();
    }

    @Override
    public List<MergeResponse> merge(List<MergeRequest> requests) {
        return List.of();
    }
}
