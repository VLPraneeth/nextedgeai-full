package com.syncari.connector.service.def;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.DocumentRequest;
import com.syncari.connector.data.DocumentResponse;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.MergeRequest;
import com.syncari.connector.data.MergeResponse;
import com.syncari.connector.data.SearchRequest;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.exception.RetriableException;

public interface DataService {

	static final int DEFAULT_CLOCK_SKEW_TOLERANCE_SECONDS = 5;

	/**
	 * Api to get records created/updated after the watermark
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public FetchResponse getByWatermark(SyncRequest request);

	/**
	 * Api to get first record's created time in epoch milli
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public long getFirstCreatedTime(SyncRequest request);

	/**
	 * Api to get records by id
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public List<EntityData> getByIds(SyncRequest request);

    /**
     * Api to get deleted records by date range. This is for synapses that have explicit API to retrieve deleted records.
     */
    @Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public FetchResponse getDeletedByWatermark(SyncRequest request);

	/**
	 * Api to insert records by entity
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public SyncResponse create(SyncRequest request);

	/**
	 * Api to update records by entity
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public SyncResponse update(SyncRequest request);

	/**
	 * Api to delete records by ids
	 */
	@Retryable(value = { RetriableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 1000))
	public SyncResponse delete(SyncRequest request);

    /**
     * Api to merge a list of entities of same type
     */
    MergeResponse merge(MergeRequest request);

	List<MergeResponse> merge(List<MergeRequest> requests);

	default boolean isSystemField(String fieldName) {
		return EntityData.SYSTEM_FIELDS.contains(fieldName);
	}

	// Used only to manipulate end time in watermark. computed = ET - skew
	default int clockSkewTolerance(ConnectorInfo connectorInfo) { return DEFAULT_CLOCK_SKEW_TOLERANCE_SECONDS; }

	// Used only to manipulate start time in watermark. computed = ST - skew. This will replay events that were previously missed
	default int lookBehindDuration(ConnectorInfo connectorInfo) { return 0; }

    // The services are supposed to implement this API.
    default DocumentResponse getFileContents(DocumentRequest syncRequest) {
        return new DocumentResponse(ByteArrayInputStream.nullInputStream(), syncRequest.getFileMetadata());
    }

    default List<EntityData> search(SearchRequest request) {
    	return List.of();
    }
}
