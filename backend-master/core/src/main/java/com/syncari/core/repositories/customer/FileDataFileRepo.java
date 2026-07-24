package com.syncari.core.repositories.customer;

import java.util.List;

import com.syncari.core.model.FileDataFile;
import com.syncari.core.repositories.SyncariRepo;

public interface FileDataFileRepo extends SyncariRepo<FileDataFile> {
	List<FileDataFile> findByFolderId(String folderId);
	boolean existsByNameAndFolderId(String name, String folderId);
}
