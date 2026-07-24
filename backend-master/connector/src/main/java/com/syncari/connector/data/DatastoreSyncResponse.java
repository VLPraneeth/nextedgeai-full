package com.syncari.connector.data;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@ToString
public class DatastoreSyncResponse {

    private List<SyncResponse> responses;
    private boolean hasMoreResponses;
}
