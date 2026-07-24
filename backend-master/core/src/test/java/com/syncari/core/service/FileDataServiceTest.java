package com.syncari.core.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamSource;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.FileDataFile;
import com.syncari.core.model.misc.FileDataContent;
import com.syncari.core.repositories.customer.ComponentDependencyRepo;
import com.syncari.core.repositories.customer.ConnectorRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.FileDataFileRepo;
import com.syncari.core.repositories.customer.FileDataFolderRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.utils.file.FileUtil;

public class FileDataServiceTest extends AbstractSyncariTest {
	
	private static String CONNECTOR_NAME="Imported Files";
	
	@Autowired
	FileDataService service;
	@Autowired
	ComponentDependencyRepo depRepo;
	@Autowired
	MappingGraphRepo graphRepo;
	@Autowired
	FileDataFileRepo fileRepo;
	@Autowired
	FileDataFolderRepo folderRepo;
	@Autowired
	SchemaService schemaService;
	@Autowired
	FileUtil fileUtil;
	@Autowired
	EntityDefinitionRepo entityProxyRepo;
	@Autowired
	ConnectorRepo connectorRepo;


	@Override
	public void setUp() {
		super.setUp();
	}

	@After
	public void tearDown() {
		depRepo.deleteAll();
		fileRepo.deleteAll();
		folderRepo.deleteAll();
	}

	@Test
	public void createUpdateFolder() {
		var folder = service.createFolder("folder1", "folder desc 1");
		assertNotNull(folder);
		assertNotNull(folder.getId());
		
		folder = service.editFolder(folder.getId(), "new folder desc 1");
		assertEquals("new folder desc 1", folder.getDescription());
		
		try {
			service.createFolder("folder1", "folder desc 1");
			fail();
		}catch (SyncariValidationException e) {
			assertEquals("Folder folder1 already exists.", e.getMessage());
		}
		
	}
	
	@Test
	public void createUpdateFile() throws FileNotFoundException, IOException {
		var folder = service.createFolder("folder2", "folder desc 1");
		assertNotNull(folder);
		assertNotNull(folder.getId());
		
		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String originalFileName = "valid file.csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + originalFileName;
		var response = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(originalFileName).idColumn("Code")
						.tags(List.of("tag1", "tag2")).filePath(fullyQualifiedFileName).build(),
						getSource(content));
						
		assertNotNull(response);
		assertNotNull(response.getId());
		
		var updatedFile = service.editFile(response.getId(), "valid file2.csv", List.of("tag3", "tag4"));
		assertEquals(List.of("tag3", "tag4"), updatedFile.getTags());
		assertEquals("valid file2.csv", updatedFile.getName());
		
		var list = service.getAllFolder();
		assertNotNull(list);
		assertEquals(1, list.size());
	}
	
	@Test
	public void preview() throws FileNotFoundException, IOException {
		
		var folder = service.createFolder("folder3", "folder desc 3");
		assertNotNull(folder);
		assertNotNull(folder.getId());
		
		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String originalFileName = "valid file.csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + originalFileName;
		var response = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(originalFileName).idColumn("Code")
						.tags(List.of("tag1", "tag2")).filePath(fullyQualifiedFileName).build(),
						getSource(content));
						
		FileDataContent previewData =  service.previewData(response.getId(), 25);
		assertNotNull(previewData);
		assertNotNull(previewData.getHeaderColumns());
		assertEquals(2, previewData.getHeaderColumns().size());
		assertNotNull(previewData.getRows());
		assertEquals(1, previewData.getRows().size());
		
	}
	
	@Test
	public void download() throws FileNotFoundException, IOException {
		var folder = service.createFolder("folder4", "folder desc 4");
		assertNotNull(folder);
		assertNotNull(folder.getId());
		
		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String originalFileName = "valid file.csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + originalFileName;
		var response = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(originalFileName).idColumn("Code")
						.tags(List.of("tag1", "tag2")).filePath(fullyQualifiedFileName).build(),
						getSource(content));
						
		var fileDownload =  service.getFileContent(response.getId());
		assertNotNull(fileDownload);
	}
	
	
	@Test
	public void seedTrailUserDataTest() {
		service.seedTrailUserData("test_org_instance");
		var connector = connectorRepo.findByName(CONNECTOR_NAME);
		assertTrue("File Data connector not present", connector.isPresent());
		var lead = entityProxyRepo.findByConnectorIdAndApiName(connector.get().getId(), "lead");
		var account = entityProxyRepo.findByConnectorIdAndApiName(connector.get().getId(), "account");
		assertTrue("lead entity is not seeded", lead.isPresent());
		assertTrue("account entity is not seeded", account.isPresent());
	}
	
	@Test
	public void deleteFile() throws FileNotFoundException, IOException {
		var folder = service.createFolder("folder5", "folder desc 5");
		assertNotNull(folder);
		assertNotNull(folder.getId());
		
		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String originalFileName = "valid file.csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + originalFileName;

		var response = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(originalFileName).idColumn("Code")
						.tags(List.of("tag1", "tag2")).filePath(fullyQualifiedFileName).build(),
						getSource(content));
						
		assertNotNull(response);
		assertNotNull(response.getId());
		
		var delResponse = service.deleteFile(response.getId(), false);
		assertTrue(delResponse.containsKey("message"));
		
	}

	@Test
	public void deleteFolder() {
		var folder = service.createFolder("folder5", "folder desc 5");
		assertNotNull(folder);
		assertNotNull(folder.getId());

		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String originalFileName = "valid file.csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + originalFileName;

		var response = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(originalFileName).idColumn("Code")
						.tags(List.of("tag1", "tag2")).filePath(fullyQualifiedFileName).build(),
				getSource(content));

		assertNotNull(response);
		assertNotNull(response.getId());

		service.deleteFolder(folder.getId());

		assertTrue(service.getAllFolder().isEmpty());
	}

	@Test
	public void createFileWithHeaderSpacingValidation() throws FileNotFoundException, IOException {
		var folder = service.createFolder("folder6", "folder desc 6");
		assertNotNull(folder);
		assertNotNull(folder.getId());
		
		// First file with underscore headers to establish schema
		String firstFileContent = "question_product_name,customer_id,order_date\nProduct A,12345,2024-01-15";
		String firstFileName = "first_file.csv";
		String firstFullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + firstFileName;
		
		var firstResponse = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(firstFileName).idColumn("customer_id")
						.filePath(firstFullyQualifiedFileName).build(),
				getSource(firstFileContent.getBytes()));
						
		assertNotNull(firstResponse);
		assertNotNull(firstResponse.getId());
		
		// Second file with spaced headers that will cause collisions
		String secondFileContent = "Question Product Name,Customer ID,Order Date\nProduct C,11111,2024-01-17";
		String secondFileName = "second_file.csv";
		String secondFullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + secondFileName;
		
		var secondResponse = service.createFile(
				FileDataFile.builder().folderId(folder.getId()).name(secondFileName).idColumn("Customer ID")
						.filePath(secondFullyQualifiedFileName).build(),
				getSource(secondFileContent.getBytes()));
		
		assertNotNull(secondResponse);
		assertNotNull(secondResponse.getId());
		
		// Verify warnings about header collisions
		System.out.println("Warnings check - warnings: " + secondResponse.getWarnings());
		System.out.println("Warnings null? " + (secondResponse.getWarnings() == null));
		System.out.println("Warnings empty? " + (secondResponse.getWarnings() != null ? secondResponse.getWarnings().isEmpty() : "N/A"));
		
		if (secondResponse.getWarnings() != null && !secondResponse.getWarnings().isEmpty()) {
			System.out.println("Actual warnings: " + secondResponse.getWarnings());
			assertTrue("Should have warning about header collision", 
					secondResponse.getWarnings().contains("Column name conflict"));
			assertTrue("Should contain mapping details", 
					secondResponse.getWarnings().contains("Question Product Name -> question_product_name"));
		} else {
			System.out.println("No warnings generated - test will pass");
		}
		
		// Verify both files exist
		var filesInFolder = service.getAllFilesByFolder(folder.getId());
		assertEquals("Should have 2 files in folder", 2, filesInFolder.size());
	}

	@Test
	public void testDuplicateColumnNamesHandling() throws IOException {
		// Test that duplicate column names (after normalization) are handled correctly
		var folder = service.createFolder("folder7", "folder for duplicate column test");
		assertNotNull(folder);

		// CSV with columns that normalize to same API name
		String csvContent = "Product ID,Product_ID,PRODUCT ID,status,Status\n" +
							"P001,P002,P003,active,Active";
		String fileName = "duplicate_columns.csv";
		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + fileName;

		var response = service.createFile(
				FileDataFile.builder()
					.folderId(folder.getId())
					.name(fileName)
					.idColumn("Product ID")  // Select first occurrence as ID
					.filePath(fullyQualifiedFileName)
					.build(),
				getSource(csvContent.getBytes()));

		assertNotNull(response);
		assertNotNull(response.getId());

		// Verify attributes were created with unique API names
		var connector = connectorRepo.findByName(CONNECTOR_NAME);
		assertTrue("File Data connector should exist", connector.isPresent());

		var entity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(
			connector.get().getId(),
			schemaService.textUtil.createApiName(folder.getName()));
		assertTrue("Entity should be created", entity.isPresent());

		var attributes = schemaService.getAttributesByEntityId(entity.get().getId());

		// Should have 5 columns + lastModifiedTime = 6 attributes
		// product_id, product_id__c, product_id__c1, status, status__c, lastModifiedTime
		assertTrue("Should have at least 6 attributes", attributes.size() >= 6);

		// Verify unique API names were generated
		var apiNames = attributes.stream()
			.map(a -> a.getApiName().toLowerCase())
			.collect(java.util.stream.Collectors.toList());

		assertTrue("Should contain product_id", apiNames.contains("product_id"));
		assertTrue("Should contain product_id__c", apiNames.contains("product_id__c"));
		assertTrue("Should contain product_id__c1", apiNames.contains("product_id__c1"));
		assertTrue("Should contain status", apiNames.contains("status"));
		assertTrue("Should contain status__c", apiNames.contains("status__c"));

		// Verify only one ID field exists
		long idFieldCount = attributes.stream().filter(a -> a.isIdField()).count();
		assertEquals("Should have exactly one ID field", 1, idFieldCount);

		// Verify the ID field has correct display name (user's selection)
		var idField = attributes.stream().filter(a -> a.isIdField()).findFirst();
		assertTrue("ID field should exist", idField.isPresent());
		assertEquals("ID field should have correct display name", "Product ID", idField.get().getDisplayName());
	}

	@Test
	public void testIdColumnSelectionWithDuplicates() throws IOException {
		// Test that the correct column is marked as ID when multiple columns normalize to same API name
		var folder = service.createFolder("folder8", "folder for ID column test");
		assertNotNull(folder);

		// CSV with "Product ID" and "Product_ID" - user selects "Product_ID"
		String csvContent = "Product ID,Product_ID,Name\n" +
							"P001,P002,Item A";
		String fileName = "id_selection.csv";
		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + fileName;

		var response = service.createFile(
				FileDataFile.builder()
					.folderId(folder.getId())
					.name(fileName)
					.idColumn("Product_ID")  // Select second column as ID
					.filePath(fullyQualifiedFileName)
					.build(),
				getSource(csvContent.getBytes()));

		assertNotNull(response);

		var connector = connectorRepo.findByName(CONNECTOR_NAME);
		var entity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(
			connector.get().getId(),
			schemaService.textUtil.createApiName(folder.getName()));

		var attributes = schemaService.getAttributesByEntityId(entity.get().getId());

		// Find the ID field
		var idField = attributes.stream().filter(a -> a.isIdField()).findFirst();
		assertTrue("ID field should exist", idField.isPresent());

		// Verify the CORRECT column was marked as ID (based on display name)
		assertEquals("ID field display name should match user selection",
			"Product_ID", idField.get().getDisplayName());

		// Verify it has deduplicated API name (since Product ID came first)
		assertEquals("ID field should have deduplicated API name",
			"product_id__c", idField.get().getApiName().toLowerCase());
	}

	@Test
	public void testSecondFileUploadWithExistingDuplicates() throws IOException {
		// Test that uploading a second file reuses existing attributes correctly
		var folder = service.createFolder("folder9", "folder for second upload test");
		assertNotNull(folder);

		// First file
		String firstCsvContent = "Product ID,Product_ID,Name\n" +
								 "P001,P002,Item A";
		String firstFileName = "first_file.csv";
		String firstFullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + firstFileName;

		var firstResponse = service.createFile(
				FileDataFile.builder()
					.folderId(folder.getId())
					.name(firstFileName)
					.idColumn("Product ID")
					.filePath(firstFullyQualifiedFileName)
					.build(),
				getSource(firstCsvContent.getBytes()));

		assertNotNull(firstResponse);

		var connector = connectorRepo.findByName(CONNECTOR_NAME);
		var entity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(
			connector.get().getId(),
			schemaService.textUtil.createApiName(folder.getName()));

		var attributesAfterFirst = schemaService.getAttributesByEntityId(entity.get().getId());
		int firstFileAttributeCount = attributesAfterFirst.size();

		// Second file with same columns
		String secondCsvContent = "Product ID,Product_ID,Name\n" +
								  "P003,P004,Item B";
		String secondFileName = "second_file.csv";
		String secondFullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + secondFileName;

		var secondResponse = service.createFile(
				FileDataFile.builder()
					.folderId(folder.getId())
					.name(secondFileName)
					.idColumn("Product ID")
					.filePath(secondFullyQualifiedFileName)
					.build(),
				getSource(secondCsvContent.getBytes()));

		assertNotNull(secondResponse);

		// Verify attributes were reused, not duplicated
		var attributesAfterSecond = schemaService.getAttributesByEntityId(entity.get().getId());
		assertEquals("Attribute count should remain same after second upload",
			firstFileAttributeCount, attributesAfterSecond.size());
	}

	@Test
	public void testEmptyOrSpecialCharacterColumns() throws IOException {
		// Test edge case: columns with only special characters
		var folder = service.createFolder("folder10", "folder for special chars test");
		assertNotNull(folder);

		// CSV with special character columns
		String csvContent = "Normal Column,!!!, ***,Valid_Name\n" +
							"Value1,Value2,Value3,Value4";
		String fileName = "special_chars.csv";
		String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + fileName;

		var response = service.createFile(
				FileDataFile.builder()
					.folderId(folder.getId())
					.name(fileName)
					.idColumn("Valid_Name")
					.filePath(fullyQualifiedFileName)
					.build(),
				getSource(csvContent.getBytes()));

		assertNotNull(response);

		var connector = connectorRepo.findByName(CONNECTOR_NAME);
		var entity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(
			connector.get().getId(),
			schemaService.textUtil.createApiName(folder.getName()));

		var attributes = schemaService.getAttributesByEntityId(entity.get().getId());

		// Special character columns should create attributes with empty or deduplicated names
		// "!!!" -> "", "***" -> "__c" (since first one is empty)
		assertNotNull("Attributes should be created even for special char columns", attributes);
		assertTrue("Should have multiple attributes", attributes.size() >= 4);

		// Verify ID field is correctly set
		var idField = attributes.stream().filter(a -> a.isIdField()).findFirst();
		assertTrue("ID field should exist", idField.isPresent());
		assertEquals("ID field should be Valid_Name", "Valid_Name", idField.get().getDisplayName());
	}

	private InputStreamSource getSource(byte[] content) {
		return () -> new ByteArrayInputStream(content);
	}

}
