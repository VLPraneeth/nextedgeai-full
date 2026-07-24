package com.syncari.core.file;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.utils.file.S3FileManager;

public class FileManagerFactoryTest extends AbstractSyncariTest {
	@Autowired
	FileManagerFactory factory;

	@Test
	public void getFileManager() {
		assertEquals(GCSFileManager.class,
				factory.getFileManager(new ReferenceDataSource(ReferenceDataSourceType.upload, null)).getClass());
		assertEquals(S3FileManager.class, factory.getFileManager(new ReferenceDataSource(ReferenceDataSourceType.s3,
				"https://<bucket-name>.s3-<region>.amazonaws.com/<file-name>")).getClass());
		try {
			factory.getFileManager(new ReferenceDataSource(null, null));
		} catch (Exception e) {
			assertEquals("Reference data source is required", e.getMessage());
		}
	}
}
