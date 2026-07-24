package com.syncari.core.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;

public class GCSFileManagerTest extends AbstractSyncariTest {
    private static final String BASE_RESOURCE_PATH = "src/test/resources/csv/";
	@Autowired
	GCSFileManager manager;

	@Test
	public void uploadInvalidFile() {
	    try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies.csv")) {
		    manager.uploadFile(fileStream, null);
		} catch (Exception e) {
			assertEquals("Filename cannot be blank", e.getMessage());
		}
		try {
		    manager.uploadFile(null, "test");
		} catch (Exception e) {
		    assertEquals("File stream cannot be blank", e.getMessage());
		}
	}
	
	@Test
	public void uploadValidFile() throws IOException {
	    String fileName = "test_movies.csv";
	    try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies.csv")) {
	        manager.uploadFile(fileStream, fileName);
	    } catch (Exception e) {
	        fail();
	    }
        InputStream file = manager.readFile(fileName);
        assertNotNull(file);
        file.close();
	    manager.deleteFile(fileName);
	    try {
	        file = manager.readFile(fileName);
        } catch (Exception e) {
            assertEquals("File with name test_movies.csv not found", e.getMessage());
        }
	}
	@Test
	public void uploadValidFileWithPaths() throws IOException {
		String fileName = "random/test_movies.csv";
		try (InputStream fileStream = new FileInputStream(BASE_RESOURCE_PATH + "movies.csv")) {
			manager.uploadFile(fileStream, fileName);
		} catch (Exception e) {
			fail();
		}
		InputStream file = manager.readFile(fileName);
		assertNotNull(file);
		file.close();
		manager.deleteFile(fileName);
		try {
			file = manager.readFile(fileName);
		} catch (Exception e) {
			assertEquals("File with name random/test_movies.csv not found", e.getMessage());
		}
	}
}
