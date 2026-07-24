package com.syncari.viper.simulation;

import com.syncari.connector.UpdateFieldResponse;
import com.syncari.connector.data.*;
import com.syncari.connector.service.def.MetadataService;

import java.util.List;
import java.util.Optional;

public class ReadonlyMetadataService implements MetadataService {
    private MetadataService delegate;

    public ReadonlyMetadataService(MetadataService delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        return delegate.describe(request);
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        return delegate.describeAll(request);
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return request.getSchema();
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        return request.getSchema();
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        //Do nothing
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {
        //Do nothing
    }

    @Override
    public UpdateFieldResponse updateField(UpdateFieldRequest request) {
        return new UpdateFieldResponse().setFieldUpdated(true).setNewSchema(request.getSchema());
    }

    @Override
    public EntitySchema updateObject(UpdateObjectRequest request) {
        return request.getSchema();
    }
}
