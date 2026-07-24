package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.TestConfig;
import com.syncari.core.event.Publisher;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.FileManagerFactory;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.functions.LookupReferenceDataFunction;
import com.syncari.core.model.Notification;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.misc.*;
import com.syncari.core.repositories.customer.NotificationRepo;
import com.syncari.core.repositories.customer.ReferenceDataMetaRepo;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.utils.SyncariMongoUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:test_application.properties")
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class ReferenceDataTest extends AbstractSyncariTest {
	@Autowired
	ReferenceDataService service;
	@Mock
	FileManagerFactory fileFactory;
	@Mock
	GCSFileManager gcsFileManager;
	@Mock
	Publisher publisher;
	@Autowired
	CustomerMongoUtils customerMongoUtils;
	@Autowired
	SyncariMongoUtils syncariMongoUtils;
	@Autowired
	NotificationService notificationService;
	@Autowired
	ReferenceDataMetaRepo repo;
	@Autowired
	UserService userService;
	@Autowired
	NotificationRepo inboxRepo;
	@Autowired
	ComponentDependencyService componentDependencyService;

	@Override
	public void setUp() {
		super.setUp();
		doReturn(gcsFileManager).when(fileFactory).getFileManager(any());
		doReturn("somepath").when(gcsFileManager).uploadFile(any(), any());
		doNothing().when(publisher).publishToGenericQueue(anyString());
		service.setFileManagerFactory(fileFactory);
		service.publisher = publisher;
	}

	@Test
	public void createMetaValidations() {
		try {
			service.createMeta(null, null, null, true);
			fail();
		} catch (Exception e) {
			assertEquals("Dataset cannot be null", e.getMessage());
		}
	}
	
	@Test
	public void getStandard() {
		assertEquals(5, service.listMeta(0).stream().filter(r -> r.isStandard()).count());
	}

	@Test
	public void createMetaS3Validations() {
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.s3, null, null, null));
			service.createMeta(refData, null, null, true);
			fail();
		} catch (Exception e) {
			assertEquals("Fully qualified FileName is required for S3", e.getMessage());
		}
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.s3, "http://test.aws.com/file.csv", null, null));
			service.createMeta(refData, null, null, true);
			fail();
		} catch (Exception e) {
			assertEquals("Access Key is required for S3", e.getMessage());
		}
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.s3, "http://test.aws.com/file.csv", "test", null));
			service.createMeta(refData, null, null, true);
			fail();
		} catch (Exception e) {
			assertEquals("Secret Key is required for S3", e.getMessage());
		}
	}

	private String getRefdatasetName() {
		return "City names dataset"+Math.random();
	}

	@Test
	public void createMetaFileUploadValidations() {
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.upload, null, null, null));
			service.createMeta(refData, null, null, true);
			fail();
		} catch (Exception e) {
			assertEquals("File is required", e.getMessage());
		}
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.upload, null, null, null));
			try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
				service.createMeta(refData, fileStream, null, true);
			}
			fail();
		} catch (Exception e) {
			assertEquals("FileName is required", e.getMessage());
		}
	}

	@Test
	public void createSyncariMetaFileUploadValidations() {
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.syncari, null, null, null));
			service.createMeta(refData, null, null, true);
			fail();
		} catch (Exception e) {
			assertEquals("File is required", e.getMessage());
		}
		try {
			ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
					new ReferenceDataSource(ReferenceDataSourceType.syncari, null, null, null));
			try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
				service.createMeta(refData, fileStream, null, true);
			}
			fail();
		} catch (Exception e) {
			assertEquals("FileName is required", e.getMessage());
		}
	}


	@Test
	public void createSyncariMetaFileUploadSucceeds() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.syncari, "valid.csv", null, null));
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City Name"));
			assertTrue(refData.getFields().containsKey("Code"));
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void createMetaFileUploadSucceeds() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
			new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City Name"));
			assertTrue(refData.getFields().containsKey("Code"));
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void createMeta_DuplicateName() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta("Airport Codes",
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			try{
				refData = service.createMeta(refData, fileStream, null, true);
				fail();
			} catch (Exception e) {
				assertEquals("Dataset with name 'Airport Codes' already exists.", e.getMessage());
			}
		}
	}

	@Test
	public void testDeleteMeta() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());

			componentDependencyService.addDependency("pipeline123", ComponentType.pipeline, refData.getId(), ComponentType.referencedata);
			List<String> depPipelines = componentDependencyService.findDependencies(refData.getId(), ComponentType.referencedata, ComponentType.pipeline);
			assertFalse(depPipelines.isEmpty());
			assertEquals("pipeline123", depPipelines.get(0));

			try {
				service.deleteMeta(refData.getId());
				fail();
			} catch (Exception e) {
				assertEquals("A referenced dataset cannot be deleted. Please remove references from all pipelines to proceed with deletion.", e.getMessage());
			}

			// delete component dependency for successful deletion
			componentDependencyService.deleteDependency("pipeline123", ComponentType.pipeline, refData.getId(), ComponentType.referencedata);
			depPipelines = componentDependencyService.findDependencies(refData.getId(), ComponentType.referencedata, ComponentType.pipeline);
			assertTrue(depPipelines.isEmpty());
			service.deleteMeta(refData.getId());

		}
	}

	@Test
	public void list() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		ReferenceDataMeta refData1 = new ReferenceDataMeta("City names dataset1",
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
		}
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData1 = service.createMeta(refData1, fileStream, null, true);
		}
		List<ReferenceDataMeta> data = service.listMeta(0);
		assertNotNull(data);
		assertTrue(data.size()>=2);
		service.deleteMeta(refData.getId());
		service.deleteMeta(refData1.getId());
	}

	@Test
	public void previewLessThan10() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			ReferenceData data = service.previewData(refData.getId());
			assertNotNull(data);
			assertEquals(2, data.getHeaderColumns().size());
			assertEquals(1, data.getRows().size());
			assertEquals("Fremont", data.getRows().get(0).get(0));
			assertEquals("1", data.getRows().get(0).get(1));
			service.deleteMeta(refData.getId());
		}
	}
	
	@Test
	public void addUpdateDeleteItem() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			long countBefore = service.customerMongoUtils.count(service.getCollectionName(refData), Optional.empty());
			List<String> id = service.addItems(refData.getId(), List.of(Map.of("City Name", "San Mateo", "Code", 2)));
			long countAfter = service.customerMongoUtils.count(service.getCollectionName(refData), Optional.empty());
			assertEquals(countBefore + 1, countAfter);
			countAfter = service.updateItems(refData.getId(), Map.of(id.get(0) , Map.of("Code", 3)));
			assertEquals(countBefore + 1, countAfter);
			countAfter = service.deleteItems(refData.getId(), id);
			assertEquals(1, countAfter);
			countAfter = service.customerMongoUtils.count(service.getCollectionName(refData), Optional.empty());
			assertEquals(countBefore, countAfter);
			service.deleteMeta(refData.getId());
		}
	}
	
	@Test
	public void addUpdateDeleteItemValidations() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try {
			service.addItems(null, null);
			fail();
		} catch (SyncariValidationException e) {
			assertEquals(e.getMessage(), "Reference data id is required");
		}
		try {
			service.addItems("123", null);
			fail();
		} catch (SyncariValidationException e) {
			assertEquals(e.getMessage(), "Reference data meta not found");
		}
	}

	@Test
	public void previewSyncariLessThan10() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.syncari, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			ReferenceData data = service.previewData(refData.getId());
			assertNotNull(data);
			assertEquals(2, data.getHeaderColumns().size());
			assertEquals(1, data.getRows().size());
			assertEquals("Fremont", data.getRows().get(0).get(0));
			assertEquals("1", data.getRows().get(0).get(1));
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void previewMoreThan10() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "preview.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
            ReferenceData data = service.previewData(refData.getId(), 10);
			assertNotNull(data);
			assertEquals(2, data.getHeaderColumns().size());
			assertEquals(10, data.getRows().size());
			assertEquals("Fremont", data.getRows().get(0).get(0));
			assertEquals("1", data.getRows().get(0).get(1));
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void previewSyncariMoreThan10() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.syncari, "preview.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
            ReferenceData data = service.previewData(refData.getId(), 10);
			assertNotNull(data);
			assertEquals(2, data.getHeaderColumns().size());
			assertEquals(10, data.getRows().size());
			assertEquals("Fremont", data.getRows().get(0).get(0));
			assertEquals("1", data.getRows().get(0).get(1));
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void extractMetaNotFound() throws FileNotFoundException, IOException {
		assertNull(service.extract("non-existant", true));
	}

	@Test
	public void extractMetaForFileUpload() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "preview.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);

			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertNull(refData.getImportDetails());
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			List<Map<String, String>> data = customerMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
			assertNotNull(data);
			assertEquals(3, data.get(0).keySet().size());
			assertEquals(14, data.size());
			assertEquals("Fremont", data.get(0).get("City Name"));
			assertEquals("1", data.get(0).get("Code"));

			List<Notification> notifications = notificationService.get(userService.getAdmins().get(0).getId(), Optional.empty());
			assertEquals(1, notifications.size());
			assertEquals("Import Completed", notifications.get(0).getSubject());
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void extractSyncariMetaForFileUpload() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.syncari, "preview.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);

			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertNull(refData.getImportDetails());
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			List<Map<String, String>> data = syncariMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
			assertNotNull(data);
			assertEquals(3, data.get(0).keySet().size());
			assertEquals(14, data.size());
			assertEquals("Fremont", data.get(0).get("City Name"));
			assertEquals("1", data.get(0).get("Code"));

			List<Notification> notifications = notificationService.get(userService.getAdmins().get(0).getId());
			assertEquals(1, notifications.size());
			assertEquals("Import Completed", notifications.get(0).getSubject());
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void extractMetaForLargeFileUpload() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta("City names dataset1",
				new ReferenceDataSource(ReferenceDataSourceType.upload, "cities.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/cities.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/cities.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);

			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertNull(refData.getImportDetails());
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			List<Map<String, String>> data = customerMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
			assertNotNull(data);
			assertEquals(8, data.get(0).keySet().size());
			assertEquals(20, data.size());
			Map<String, Map<String, String>> byCanonicalName = data.stream().collect(Collectors.toMap(r -> r.get("Canonical Name"), r -> r));
			Map<String, String> expected = Map.of(
					"Criteria ID", "1000004", "Name", "The Valley",
					"Canonical Name", "The Valley,Anguilla", "Parent ID", "2660",
					"Country Code", "AI", "Target Type", "City", "Status", "Active");

			Map<String, String> anguilla = byCanonicalName.get("The Valley,Anguilla");
			expected.forEach((key, value) -> assertEquals(value, anguilla.get(key)));
			List<Notification> notifications = notificationService.get(userService.getAdmins().get(0).getId());
			assertEquals(1, notifications.size());
			assertEquals("Import Completed", notifications.get(0).getSubject());
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void extractMetaForFileUploadError() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "preview.csv", null, null));
		doThrow(new RuntimeException()).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);

			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertNotNull(refData.getImportDetails());
			assertEquals(DataImportStatus.ERROR, refData.getStatus());

			List<Map<String, String>> data = customerMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
			assertNotNull(data);
			assertEquals(0, data.size());

			List<Notification> notifications = notificationService.get(SyncariContext.getUser().getId());
			assertEquals(0, notifications.size());
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void lookUp() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
			new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City Name"));
			assertTrue(refData.getFields().containsKey("Code"));

			service.extract(refData.getId(), true);

			String operator = "exactMatch";

			Object result = service.lookUp(refData.getId(), "City Name", "Fremont", "Code", operator, false, new HashMap<>());
			assertEquals("1", result);
			result = service.lookUp(refData.getId(), null, "Fremont", "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", null, "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			Object caseInsensitveLookupResult = service.lookUp(refData.getId(), "City Name", "fremont", "Code", operator, true, new HashMap<>());
			assertEquals("1", caseInsensitveLookupResult);
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void lookUpSpecialFieldNames() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid_specialchars.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid_specialchars.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(3, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City-Name"));
			assertTrue(refData.getFields().containsKey("Code"));
			assertTrue(refData.getFields().containsKey("State_Name"));

			service.extract(refData.getId(), true);

			String operator = "exactMatch";

			Object result = service.lookUp(refData.getId(), "City-Name", "Fremont", "Code", operator, false, new HashMap<>());
			assertEquals("1", result);
			result = service.lookUp(refData.getId(), "State_Name", "CA", "Code", operator, false, new HashMap<>());
			assertEquals("1", result);
			result = service.lookUp(refData.getId(), null, "Fremont", "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City-Name", null, "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City-Name", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			Object caseInsensitveLookupResult = service.lookUp(refData.getId(), "City-Name", "fremont", "Code", operator, true, new HashMap<>());
			assertEquals("1", caseInsensitveLookupResult);
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void lookUpContains() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City Name"));
			assertTrue(refData.getFields().containsKey("Code"));

			service.extract(refData.getId(), true);

			String operator = LookupReferenceDataFunction.CONTAINS;

			Object result = service.lookUp(refData.getId(), "City Name", "Frem", "Code", operator, false, new HashMap<>());
			assertEquals("1", result);
			result = service.lookUp(refData.getId(), null, "Fremont", "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", null, "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			Object caseInsensitveLookupResult = service.lookUp(refData.getId(), "City Name", "fReM", "Code", operator, false, new HashMap<>());
			assertEquals("1", caseInsensitveLookupResult);
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void lookUpIn() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.upload, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City Name"));
			assertTrue(refData.getFields().containsKey("Code"));

			service.extract(refData.getId(), true);

			String operator = LookupReferenceDataFunction.IN;

			Object result = service.lookUp(refData.getId(), "City Name", "Newark and Fremont", "Code", operator, false, new HashMap<>());
			assertEquals("1", result);
			result = service.lookUp(refData.getId(), null, "Newark and Fremont", "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", null, "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", "Newark and Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City", "Newark and Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void syncariLookUp() throws FileNotFoundException, IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta(getRefdatasetName(),
				new ReferenceDataSource(ReferenceDataSourceType.syncari, "valid.csv", null, null));
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/valid.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/valid.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			assertNotNull(refData.getId());
			assertEquals(DataImportStatus.NEW, refData.getStatus());
			assertEquals(2, refData.getFields().size());
			assertTrue(refData.getFields().containsKey("City Name"));
			assertTrue(refData.getFields().containsKey("Code"));


			String operator = "exactMatch";
			service.extract(refData.getId(), true);

			Object result = service.lookUp(refData.getId(), "City Name", "Fremont", "Code", operator, false, new HashMap<>());
			assertEquals("1", result);
			result = service.lookUp(refData.getId(), null, "Fremont", "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", null, "Code", operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City Name", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			result = service.lookUp(refData.getId(), "City", "Fremont", null, operator, false, new HashMap<>());
			assertNull(result);
			Object caseInsensitveLookupResult = service.lookUp(refData.getId(), "City Name", "fremont", "Code", operator, true, new HashMap<>());
			assertEquals("1", caseInsensitveLookupResult);
			service.deleteMeta(refData.getId());
		}
	}

	@Test
	public void updateMetaWithNewValidFile() throws IOException {
		ReferenceDataMeta refData = new ReferenceDataMeta("city-names",
				new ReferenceDataSource(ReferenceDataSourceType.upload, "preview.csv", null, null));
		// File has 2 columns (City Name and Code)
		// 14 rows
		// First-row contents -> Fremont,1
		InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
		doReturn(fileStream1).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertNull(refData.getImportDetails());
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			List<Map<String, String>> data = customerMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
			assertEquals(14, data.size());
			assertEquals("Fremont", data.get(0).get("City Name"));
			assertEquals("1", data.get(0).get("Code"));

			List<Notification> notifications = notificationService.get(userService.getAdmins().get(0).getId());
			assertEquals(1, notifications.size());
			assertEquals("Import Completed", notifications.get(0).getSubject());
		}
		// File has 3 columns (City Name, Code and Key)
		// 17 rows
		// First-row contents -> Fremont,18,f
		InputStream fileStream2 = new FileInputStream("src/test/resources/csv/preview-three-col.csv");
		doReturn(fileStream2).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview-three-col.csv")) {
			refData = service.updateMeta(refData, fileStream, null);
			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertNull(refData.getImportDetails());
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			List<Map<String, String>> data = customerMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
			assertEquals(17, data.size());
			assertEquals("Fremont", data.get(0).get("City Name"));
			assertEquals("18", data.get(0).get("Code"));
			assertEquals("f", data.get(0).get("Key"));

			List<Notification> notifications = notificationService.get(userService.getAdmins().get(0).getId());
			assertEquals(2, notifications.size());
			assertEquals("Import Completed", notifications.get(0).getSubject());
			service.deleteMeta(refData.getId());
        }
	}

    @Test
    public void updateMetaWithNewInvalidFile() throws IOException {
        ReferenceDataMeta refData = new ReferenceDataMeta("city-names",
                new ReferenceDataSource(ReferenceDataSourceType.upload, "preview.csv", null, null));
        // File has 2 columns (City Name and Code)
        // 14 rows
        // First-row contents -> Fremont,1
        InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
        doReturn(fileStream1).when(gcsFileManager).readFile(any());
        try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
            refData = service.createMeta(refData, fileStream, null, true);
            refData = service.extract(refData.getId(), true);
            assertNotNull(refData);
            assertNull(refData.getImportDetails());
            assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

            List<Map<String, String>> data = customerMongoUtils.readMany(service.getCollectionName(refData), 20, Optional.empty());
            assertEquals(14, data.size());
            assertEquals("Fremont", data.get(0).get("City Name"));
            assertEquals("1", data.get(0).get("Code"));

            List<Notification> notifications = notificationService.get(userService.getAdmins().get(0).getId());
            assertEquals(1, notifications.size());
            assertEquals("Import Completed", notifications.get(0).getSubject());
        }
        // File has 1 column (City Name)
        // 14 rows
        // First-row contents -> Fremont
        InputStream fileStream2 = new FileInputStream("src/test/resources/csv/preview-one-col.csv");
        doReturn(fileStream2).when(gcsFileManager).readFile(any());
        try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview-one-col.csv")) {
            refData = service.updateMeta(refData, fileStream, null);
        } catch(Exception e) {
            assertEquals("The column(s) [Code] are missing in the uploaded CSV file", e.getMessage());
            service.deleteMeta(refData.getId());
        }
    }

    @Test
    public void updateMetaWhenCollectionNameIsNull() throws IOException {
        ReferenceDataMeta refData = new ReferenceDataMeta("city-names",
                new ReferenceDataSource(ReferenceDataSourceType.upload, "preview.csv", null, null));
        // File has 2 columns (City Name and Code)
        // 14 rows
        // First-row contents -> Fremont,1
        InputStream fileStream1 = new FileInputStream("src/test/resources/csv/preview.csv");
        doReturn(fileStream1).when(gcsFileManager).readFile(any());
        String datasetCollectionName = service.getCollectionName(refData);
        try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview.csv")) {
            refData = service.createMeta(refData, fileStream, null, true);
            refData = service.extract(refData.getId(), true);
            assertNotNull(refData);
            assertNull(refData.getImportDetails());
            assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

            String currentCollectionName = refData.getDatasetCollectionName();
            assertEquals(datasetCollectionName, currentCollectionName);
            List<Map<String, String>> data = customerMongoUtils.readMany(currentCollectionName, 20, Optional.empty());
            assertEquals(14, data.size());
        }
        // Setting the collectionName to null to simulate the scenario
        refData.setDatasetCollectionName(null);

        // File has 3 columns (City Name, Code and Key)
        // 17 rows
        // First-row contents -> Fremont,18,f
        InputStream fileStream2 = new FileInputStream("src/test/resources/csv/preview-three-col.csv");
        doReturn(fileStream2).when(gcsFileManager).readFile(any());
        try (InputStream fileStream = new FileInputStream("src/test/resources/csv/preview-three-col.csv")) {
            refData = service.updateMeta(refData, fileStream, null);
            refData = service.extract(refData.getId(), true);
            assertNotNull(refData);
            assertNull(refData.getImportDetails());
            assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

            String currentCollectionName = refData.getDatasetCollectionName();
            assertNotEquals(datasetCollectionName, currentCollectionName);
            List<Map<String, String>> data = customerMongoUtils.readMany(currentCollectionName, 20, Optional.empty());
            assertEquals(17, data.size());
            service.deleteMeta(refData.getId());
        }
    }

    @Test
	public void testCreateUpdateWithBomHandling() throws IOException{
		ReferenceDataMeta refData = new ReferenceDataMeta("bom-test",
				new ReferenceDataSource(ReferenceDataSourceType.upload, "file-with-bom.csv", null, null));

		InputStream withBom = new FileInputStream("src/test/resources/csv/file-with-bom.csv");
		doReturn(withBom).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/file-with-bom.csv")) {
			refData = service.createMeta(refData, fileStream, null, true);
			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			assertTrue(refData.getFields().containsKey("key"));
		}
		refData.setDatasetCollectionName(null);

		InputStream withoutBom = new FileInputStream("src/test/resources/csv/file-without-bom.csv");
		doReturn(withoutBom).when(gcsFileManager).readFile(any());
		try (InputStream fileStream = new FileInputStream("src/test/resources/csv/file-without-bom.csv")) {
			// update successful with file without bom char
			refData = service.updateMeta(refData, fileStream, null);
			refData = service.extract(refData.getId(), true);
			assertNotNull(refData);
			assertEquals(DataImportStatus.ACTIVE, refData.getStatus());

			// header with bom still remains same and not updated with the incoming file without bom char
			assertTrue(refData.getFields().containsKey("key"));
		}
	}

//	@Test
	public void extractMetaForS3() {
		// TODO
	}
}
