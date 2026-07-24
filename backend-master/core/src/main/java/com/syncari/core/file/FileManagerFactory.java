package com.syncari.core.file;

import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.utils.file.FileManager;
import com.syncari.utils.file.S3FileManager;

@Component
public class FileManagerFactory {
	@Autowired
	GCSFileManager gcsFileManager;

	@Autowired
	Storage cfStorageService;

	public FileManager getFileManager(ReferenceDataSource source) {
		if (source == null || source.getType() == null)
			throw new RuntimeException("Reference data source is required");
		switch (source.getType()) {
		case upload:
		case syncari:
			return gcsFileManager;
		case s3:
			return new S3FileManager(source.getLocation(), source.getAccessKey(), source.getSecretKey());

		default:
			throw new RuntimeException("Unknown file type");
		}
	}
}
