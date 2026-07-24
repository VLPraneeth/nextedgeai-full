package com.syncari.core.repositories.customer;

import java.util.Optional;

import com.syncari.core.model.FileDataFolder;
import com.syncari.core.repositories.SyncariRepo;

public interface FileDataFolderRepo extends SyncariRepo<FileDataFolder> {
	Optional<FileDataFolderRepo> findByName(String folderName);
}
