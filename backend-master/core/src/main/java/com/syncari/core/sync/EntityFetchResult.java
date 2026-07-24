package com.syncari.core.sync;

import com.syncari.connector.data.EntitySchema;
import com.syncari.connector.data.FetchResponse;
import com.syncari.connector.data.SyncRequest;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Watermark;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class EntityFetchResult {
    private final EntityDefinition entityDefinition;
    private final  SyncRequest request;
    private final FetchResponse response;
    private final Connector connector;
    private final EntitySchema schema;
    private final Watermark watermark;
    private final boolean deletedRecordsBatch;
}
