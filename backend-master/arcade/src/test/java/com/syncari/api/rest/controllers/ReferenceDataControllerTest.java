package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.misc.*;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.file.FileUtil;
import com.syncari.core.schema.Schema;
import net.minidev.json.JSONObject;

import org.json.JSONException;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import com.syncari.core.model.ComponentDependency;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.ComponentDependencyRepo;
import com.syncari.core.repositories.customer.MappingGraphRepo;
import com.syncari.core.repositories.customer.ReferenceDataMetaRepo;
import org.springframework.web.multipart.MultipartFile;

public class ReferenceDataControllerTest extends AbstractSyncariTest {
	@Autowired
	ReferenceDataController controller;
	@Autowired
	ComponentDependencyRepo depRepo;
	@Autowired
	MappingGraphRepo graphRepo;
	@Autowired
	ReferenceDataMetaRepo refRepo;
	@MockBean
	SchemaService schemaService;
	@Autowired
	FileUtil fileUtil;

	@Override
	public void setUp() {
		when(schemaService.getSyncariSchema()).thenReturn(new Schema());
		super.setUp();
	}

	@After
	public void tearDown() {
		depRepo.deleteAll();
		refRepo.deleteAll();
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_REFERENCE_DATA, WRITE_REFERENCE_DATA })
	public void list() {
		ReferenceDataMeta ref = new ReferenceDataMeta("City Names",
				new ReferenceDataSource(ReferenceDataSourceType.upload, "city_names.csv", "", ""), DataImportStatus.NEW,
				"", Map.of(), 0L, "dataset_City_Names", false);
		AttributeDefinition accountAttribute = new AttributeDefinition();
		accountAttribute.setId("accountNameAttributeId");
		accountAttribute.setEntityId("accountId");
		when(schemaService.getActiveAttribute(accountAttribute.getId())).thenReturn(Optional.of(accountAttribute));
		ReferenceDataMeta savedRef = refRepo.save(ref);
		MappingGraph graph = new MappingGraph();
		graph.setTargetId(accountAttribute.getId());
		graph.setName("Account Name Pipeline");
		graph.setScope(Scope.ATTRIBUTE);
		MappingGraph savedGraph = graphRepo.save(graph);
		ComponentDependency dep = new ComponentDependency(savedGraph.getId(), ComponentType.pipeline, savedRef.getId(),
				ComponentType.referencedata);
		depRepo.save(dep);
		List<com.syncari.api.rest.controllers.data.ReferenceDataMeta> list = controller.list();
		assertTrue(list.size() >= 1);
		verify(schemaService).getActiveAttribute(accountAttribute.getId());
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_REFERENCE_DATA, WRITE_REFERENCE_DATA })
	public void testInvalidDatasetName() {
		String fileName = "valid file.csv";
		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String name = "valid file.csv";
		String originalFileName = "valid file.csv";
		String contentType = "text/csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		MultipartFile result = new MockMultipartFile(name, originalFileName, contentType, content);

		ResponseEntity response = controller
				.create(" ", ReferenceDataSourceType.upload.toString(), null, null, fileName, result);
		assertEquals(HttpStatus.BAD_REQUEST ,response.getStatusCode());
		assertTrue(((JSONObject)response.getBody()).get("message").toString().contains("Please provide valid dataset name"));
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_REFERENCE_DATA, WRITE_REFERENCE_DATA })
	public void testFileNameWithSpaces() throws FileNotFoundException, IOException {
		String fileName = "valid file.csv";
		Path path = Paths.get("src/test/resources/csv/valid file.csv");
		String name = "valid file.csv";
		String originalFileName = "valid file.csv";
		String contentType = "text/csv";
		byte[] content = null;
		try {
			content = Files.readAllBytes(path);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		MultipartFile result = new MockMultipartFile(name, originalFileName, contentType, content);

		ResponseEntity<com.syncari.api.rest.controllers.data.ReferenceDataMeta> response = controller
				.create("ValidFileSet", ReferenceDataSourceType.upload.toString(), null, null, fileName, result);
		com.syncari.api.rest.controllers.data.ReferenceDataMeta refData = response.getBody();
		List<com.syncari.api.rest.controllers.data.ReferenceDataMeta> list = controller.list();
		assertTrue(list.size() >= 1);
		ReferenceData data = controller.preview(refData.getId(), 25);
		assertNotNull(data);
		assertEquals(2, data.getHeaderColumns().size());
		assertEquals(1, data.getRows().size());
		assertEquals("Fremont", data.getRows().get(0).get(0));
		assertEquals("1", data.getRows().get(0).get(1));
		controller.delete(refData.getId());
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_REFERENCE_DATA, WRITE_REFERENCE_DATA })
	public void testSupportedContentType() throws FileNotFoundException, IOException {
		List<String> supportedContentType = List.of("text/csv", "application/csv", "application/octet-stream", "application/vnd.ms-excel");

		supportedContentType.forEach(contentType -> {
			String fileName = "valid file.csv";
			Path path = Paths.get("src/test/resources/csv/valid file.csv");
			String name = "valid file.csv";
			String originalFileName = "valid file.csv";
			byte[] content = null;
			try {
				content = Files.readAllBytes(path);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			MultipartFile result = new MockMultipartFile(name, originalFileName, contentType, content);

			ResponseEntity<com.syncari.api.rest.controllers.data.ReferenceDataMeta> response = controller
					.create("SupportedReferenceFileSet", ReferenceDataSourceType.upload.toString(), null, null, fileName, result);
			com.syncari.api.rest.controllers.data.ReferenceDataMeta refData = response.getBody();
			List<com.syncari.api.rest.controllers.data.ReferenceDataMeta> list = controller.list();
			assertTrue(list.size() >= 1);
			controller.delete(refData.getId());
		});
	}

	@Test
	@WithMockUser(username = "admin", authorities = { READ_REFERENCE_DATA, WRITE_REFERENCE_DATA })
	public void validations() throws FileNotFoundException, IOException, JSONException {
		String fileName = "valid file.csv";
		byte[] content = Files.readAllBytes(Paths.get("src/test/resources/csv/valid file.csv"));
		
		MultipartFile result = new MockMultipartFile(fileName, fileName, "invalid/csv", content);
		ResponseEntity response = controller
				.create("ValidFileSet", ReferenceDataSourceType.upload.toString(), null, null, fileName, result);
		assertTrue(((JSONObject)response.getBody()).get("message").toString().contains("Unsupported content type"));
		
		result = new MockMultipartFile(fileName, fileName, "text/csv", content);
		response = controller
				.create("ValidFileSet", ReferenceDataSourceType.upload.toString(), null, null, fileName, null);
		assertTrue(((JSONObject)response.getBody()).get("message").toString().contains("File is required"));
	}
	
	@Test
	public void sanitizedName() throws FileNotFoundException, IOException, JSONException {
		assertEquals("valid_file.csv", fileUtil.sanitizeFileName("valid file.csv"));
		assertEquals("_test.csv", fileUtil.sanitizeFileName("&test.csv"));
		assertEquals("test_.csv", fileUtil.sanitizeFileName("test^^.csv"));
		assertEquals("test_.csv", fileUtil.sanitizeFileName("test++^%$#.csv"));
	}
	
	@Test
	@WithMockUser(username = "admin", authorities = { READ_REFERENCE_DATA, WRITE_REFERENCE_DATA })
	public void getStandard() {
		assertEquals(5, controller.list().stream().filter(r -> r.isStandard()).count());
	}
}
