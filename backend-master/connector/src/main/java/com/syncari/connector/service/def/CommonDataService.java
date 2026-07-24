package com.syncari.connector.service.def;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.syncari.connector.EntityData;
import com.syncari.connector.ListBasedIterator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public interface CommonDataService extends DataService {
	public static final int POST_BATCH_SIZE = 199;

	default MergeResponse upsertWinner(MergeRequest request) {
		MergeResponse response = new MergeResponse();
		assert request.getWinner() != null;
		SyncRequest newRequest = new SyncRequest().Builder(request.getConnector(), request.getEntitySchema());
		newRequest.addData(request.getConnector().getId(), request.getWinner());
		if (StringUtils.isBlank(request.getWinner().getId())) {
			// Create the winner
			response.setWinnerResult(create(newRequest));
		} else {
			// Update the winner
			response.setWinnerResult(update(newRequest));
		}
		assert (response.getWinnerResult().getResults() != null && !response.getWinnerResult().getResults().isEmpty());
		return response;
	}
	default MergeResponse merge(MergeRequest request) {
		// TODO reparent if there are any references to the losers
		try {
			MergeResponse response = upsertWinner(request);
			// Call the delete on losers
			request.getLosers().forEach(l -> {
				SyncRequest deleteRequest = new SyncRequest().Builder(request.getConnector(), request.getEntitySchema());
				deleteRequest.addData(request.getConnector().getId(), l);
				response.setLoserResult(delete(deleteRequest));
			});
			return response;
		} catch (Exception e) {
			return new MergeResponse().setWinnerResult(new SyncResponse(false)
					.setResults(List.of(new Result(false, request.getWinner().getId(), request.getWinner().getSyncariEntityId())))
					.setErrors(List.of(e.getMessage())));
		}
	}

	default List<MergeResponse> merge(List<MergeRequest> requests) {
		return requests.stream().map(mr->{
			return merge(mr);
		}).collect(Collectors.toList());
	}

	default boolean isSystemField(String fieldName) {
		return EntityData.SYSTEM_FIELDS.contains(fieldName);
	}

    default FetchResponse getDeletedByWatermark(SyncRequest request) {
        // The default implementation sends an empty iterator, the actual override methods to implement this.
        return new FetchResponse(request.getWatermark(), new ListBasedIterator(new ArrayList<>(),request.getWatermark()));
    }

	default void close(CloseContext context) {
	}

}
