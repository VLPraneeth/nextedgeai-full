package com.syncari.connector.service;

import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.FileService;
import com.syncari.connector.service.def.MetadataService;

public interface FileSystemService extends CommonDataService, MetadataService, FileService {
    SyncResponse writeFile(SyncRequest request, boolean createUUID, String operation, String baseFolder);
}
