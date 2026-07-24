package com.syncari.connector.service.def;

import com.syncari.connector.UpdateFieldResponse;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.RetriableException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.List;
import java.util.Optional;

public interface MetadataService {

	/**
	 * Api to describe the entity (with attributes) metadata
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public Optional<EntitySchema> describe(DescribeRequest request);

	/**
	 * Api to describe all entities (with attributes) metadata
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public List<EntitySchema> describeAll(DescribeAllRequest request);

	/**
	 * Api to create an object in the end system
	 */
	public EntitySchema createObject(CreateObjectRequest request);

	/**
     * Api to create a field in the end system
     */
    public AttributeSchema createField(CreateFieldRequest request);

	/**
	 * Api to create multiple fields in the end system
	 */
	default CreateFieldsResponse createFields(CreateFieldsRequest request) {
		request.getSchemas().forEach(schema ->
				createField(new CreateFieldRequest(request.getEntityName(), request.getConnector(), schema))
		);
		return new CreateFieldsResponse(request.getEntityName(), request.getConnector(), request.getSchemas());
	}

	/**
	 * Api to delete a field in the end system
	 */
	public void deleteField(DeleteFieldRequest request);
	
	/**
	 * Api to delete an entity in the end system
	 */
	default void deleteObject(DeleteObjectRequest request) {
	    
	}

	/**
	 * Api to delete an entity in the end system
	 */
	default void truncateObject(DeleteObjectRequest request) {

	}
    
    /**
     * Api to update a field in the end system
     */
	default UpdateFieldResponse updateField(UpdateFieldRequest request) {
        return new UpdateFieldResponse().setFieldUpdated(true).setNewSchema(request.getSchema());
    }
    
    /**
     * Api to update an entity in the end system
     */
    default EntitySchema updateObject(UpdateObjectRequest request) {
        return request.getSchema();
	}
}
