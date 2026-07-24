package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.studio.DataFilterDTO;
import com.syncari.api.rest.controllers.data.studio.DataFilterResponse;
import com.syncari.api.rest.controllers.data.studio.DataQueryResponse;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.EntityScore;
import com.syncari.connector.FieldScore;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.IdMapping;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.service.*;
import com.syncari.core.utils.ExternalIdVisitor;
import com.syncari.restutils.data.EntityRecord;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.DateUtil;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import javax.xml.bind.DatatypeConverter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.syncari.core.security.Permissions.*;
import static com.syncari.utils.I18n.i18n;
import static org.junit.Assert.*;

@Slf4j
public class DatastudioControllerTest extends AbstractSyncariTest {
    private static final String DATA_STUDIO_ACCOUNT = "DataStudioAccount";
    @Autowired
    private DatastudioController controller;
    @Autowired
    SchemaService schemaService;
    ObjectMapper mapper = new ObjectMapper();
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Autowired
    EntityDefinitionRepo entityProxyRepo;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService metaService;
    @Autowired
    IdMappingRepo mappingRepo;
    @Autowired
    GCSFileManager gcsFileManager;
    @Autowired
    EntityRepoService service;
    static Connector c1;
    static Connector c2;
    static EntityDefinition entityDef;
    static EntityDefinition hubspotEntity;
    static EntityDefinition netsuiteEntity;
    static EntityDefinition updateEntity;
    static EntityData e1;
    static EntityData e2;
    static EntityData e3;
    static EntityData e4;
    @Autowired
    ProfileController profileController;
    @Autowired
    MappingGraphService mappingGraphService;
    @Autowired
    ObjectTransformer transformer;
    private int expectedColumns;

    @Override
    public void setUp() {
        super.setUp();
        if(c1 == null) {
            c1 = new Connector("Hubspot", metaService.findByName(Constants.HUBSPOT).get().getId(), "");
            c1 = connectorService.save(c1);
            c2 = new Connector("Netsuite", metaService.findByName(Constants.NETSUITE).get().getId(), "");
            c2 = connectorService.save(c2);
            entityDef = getEntity();
            hubspotEntity = getHubspotEntity();
            netsuiteEntity = getNetsuiteEntity();
            updateEntity = getEntityForValidation();
            createRecords();
        }
        expectedColumns = 9;
    }

    @Override
    public void tearDown() {
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void validations() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "test1")
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        try {
            controller.query(entityDef.getId(), null, null, PageDirection.previous.name(), 3, null, null, base64);
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Cursor is required to go to previous page", e.getMessage());
        }
        try {
            controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, "Invalid json");
            fail();
        } catch (SyncariValidationException e) {
            assertEquals("Search predicate is invalid", e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryNoFilter() throws Exception {
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, null);
        assertEquals(3, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertTrue(result.getPageInfo().isHasMore());

        assertTrue(expectedColumns < result.getMetadata().getFields().size());
        assertTrue(result.getMetadata().getSelectedColumns().size() >= 2);
        assertTrue(result.getMetadata().getFields().containsKey("Name"));
        assertTrue(result.getMetadata().getFields().containsKey("Age"));
        assertTrue(result.getMetadata().getFields().containsKey("Website"));
        assertEquals(1, result.getPageInfo().getSorting().size());
        assertEquals("Id", result.getPageInfo().getSorting().get(0).getColumnName());
        assertTrue((boolean)result.getPageInfo().getSorting().get(0).isAscending());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void downloadDataWithUnicode() throws Exception {
        ResponseEntity<Resource> download = controller.download(entityDef.getId(), null);
        InputStream stream = download.getBody().getInputStream();
        List<List<String>> rows = new CsvUtils().getRows(stream, 10, new CSVOptions());
        assertEquals(4, rows.size());
        assertEquals(4,rows.get(0).size());
        assertTrue(rows.get(0).contains("弗里蒙特"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_PROFILE})
    public void downloadDataWithDateFieldsFormatted() throws Exception {
        // First, ensure LastModified column is selected for export
        profileController.updateDataStudioPreference(entityDef.getId(),
            new LinkedHashSet<>(Arrays.asList(
                Map.of("columnName", "Name", "isSelected", true),
                Map.of("columnName", "LastModified", "isSelected", true)
            )));

        // Verify that existing record with LastModified date field is properly formatted
        ResponseEntity<Resource> download = controller.download(entityDef.getId(), null);
        InputStream stream = download.getBody().getInputStream();
        List<List<String>> rows = new CsvUtils().getRows(stream, 20, new CSVOptions());

        // Verify we have the expected data
        assertTrue("Should have multiple rows", rows.size() > 1);

        // Check all rows for any date values - they should be in ISO 8601 format
        boolean foundDateValue = false;
        for (List<String> row : rows) {
            for (String cell : row) {
                if (cell != null && !cell.isEmpty()) {
                    // Check if this looks like a date value that should NOT be in Java toString format
                    // Java's Date.toString() produces format like: "Tue Dec 15 20:35:54 IST 2020"
                    if (cell.matches(".*\\s+(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\\s+.*") &&
                        cell.matches(".*\\s+(IST|PST|UTC|EST|CST|MST)\\s+.*")) {
                        fail("Found date in Java toString format (unsortable): " + cell);
                    }

                    // Check if this is an ISO 8601 formatted date
                    if (cell.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")) {
                        foundDateValue = true;
                        // Verify it doesn't contain day-of-week abbreviations (Java toString indicator)
                        assertFalse("ISO date should not contain day abbreviation: " + cell,
                            cell.contains("Mon ") || cell.contains("Tue ") || cell.contains("Wed ") ||
                            cell.contains("Thu ") || cell.contains("Fri ") || cell.contains("Sat ") ||
                            cell.contains("Sun "));
                    }
                }
            }
        }

        // Verify we actually checked some date values
        assertTrue("Should have found at least one date value in CSV to verify formatting", foundDateValue);

        // Reset preferences
        profileController.updateDataStudioPreference(entityDef.getId(),
            new LinkedHashSet<>(Arrays.asList(
                Map.of("columnName", "Name", "isSelected", true),
                Map.of("columnName", "Website", "isSelected", true),
                Map.of("columnName", "Age", "isSelected", true),
                Map.of("columnName", "City", "isSelected", true)
            )));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryStartsWithFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "starts_with",
                "right", Map.of("type", "literal", "value", "Test3")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());

        eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "starts_with",
                "right", Map.of("type", "literal", "value", "test4 ")
                );
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());

        eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "starts_with",
                "right", Map.of("type", "literal", "value", "te")
                );
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(3, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertTrue(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryOnOrAfterFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "datetime", "type", "variable", "value", entityDef.getFieldByName("LastModified").getId()),
                "operator", "gte",
                "right", Map.of("type", "literal", "value", "2020-12-18T20:35:54.828Z")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(0, result.getRecords().size());
        assertFalse(result.getPageInfo().isHasMore());

        eq = Map.of(
                "left", Map.of("datatype", "datetime", "type", "variable", "value", entityDef.getFieldByName("LastModified").getId()),
                "operator", "gte",
                "right", Map.of("type", "literal", "value", "2020-12-14T20:35:54.828Z")
                );
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryOnOrBeforeFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "datetime", "type", "variable", "value", entityDef.getFieldByName("LastModified").getId()),
                "operator", "lte",
                "right", Map.of("type", "literal", "value", "2020-12-18T20:35:54.828Z")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertFalse(result.getPageInfo().isHasMore());

        eq = Map.of(
                "left", Map.of("datatype", "datetime", "type", "variable", "value", entityDef.getFieldByName("LastModified").getId()),
                "operator", "lte",
                "right", Map.of("type", "literal", "value", "2020-12-14T20:35:54.828Z")
                );
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(0, result.getRecords().size());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryNumberFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "integer", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(2, result.getRecords().size());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryNumberFilterWithStringAsInput() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "integer", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "abcd")
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(0, result.getRecords().size());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryContainsFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "contains",
                "right", Map.of("type", "literal", "value", "est")
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 5, null, null, base64);
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryInFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "in",
                "right", Map.of("type", "literal", "value",  Arrays.asList("test1", "qqq"))
        );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 5, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_PROFILE})
    public void columnList() throws Exception {
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, null);
        assertEquals(3, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertTrue(result.getPageInfo().isHasMore());

        assertEquals(expectedColumns, result.getMetadata().getFields().size());
        // lists all columns by default
        assertEquals(expectedColumns, result.getMetadata().getSelectedColumns().size());
        assertTrue(result.getMetadata().getSelectedColumns().contains("Name"));
        assertTrue(result.getMetadata().getSelectedColumns().contains("Age"));
        assertTrue(result.getMetadata().getSelectedColumns().contains("Website"));
        assertTrue(result.getMetadata().getFields().containsKey("Name"));
        assertTrue(result.getMetadata().getFields().containsKey("Age"));
        assertTrue(result.getMetadata().getFields().containsKey("Website"));
        assertEquals(1, result.getPageInfo().getSorting().size());
        assertEquals("Id", result.getPageInfo().getSorting().get(0).getColumnName());
        assertTrue((boolean)result.getPageInfo().getSorting().get(0).isAscending());

        profileController.updateDataStudioPreference(entityDef.getId(), new LinkedHashSet<>(Arrays.asList(Map.of("columnName", "Website", "isSelected", true),
            Map.of("columnName", "Age", "isSelected", true))));
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, null);
        assertEquals(3, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertTrue(result.getPageInfo().isHasMore());
        assertEquals(expectedColumns, result.getMetadata().getFields().size());
        assertTrue(result.getMetadata().getFields().containsKey("Name"));
        assertTrue(result.getMetadata().getFields().containsKey("Age"));
        assertTrue(result.getMetadata().getFields().containsKey("Website"));
        // lists only user selected columns
        assertEquals(2, result.getMetadata().getSelectedColumns().size());
        assertFalse(result.getMetadata().getSelectedColumns().contains("Name"));
        assertTrue(result.getMetadata().getSelectedColumns().contains("Age"));
        assertTrue(result.getMetadata().getSelectedColumns().contains("Website"));

        profileController.updateDataStudioPreference(entityDef.getId(), new LinkedHashSet<>(Arrays.asList(Map.of("columnName", "Name", "isSelected", true), Map.of("columnName", "Website", "isSelected", true), Map.of("columnName", "Age", "isSelected", true),Map.of("columnName", "City", "isSelected", true))));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void querySimpleFilterNoResult() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "not found")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(0, result.getRecords().size());
        assertNull(result.getPageInfo().getEnd());
        assertNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());

        assertEquals(expectedColumns, result.getMetadata().getFields().size());
        assertTrue(result.getMetadata().getSelectedColumns().size() >= 2);
        assertTrue(result.getMetadata().getFields().containsKey("Name"));
        assertTrue(result.getMetadata().getFields().containsKey("Age"));
        assertTrue(result.getMetadata().getFields().containsKey("Website"));
        assertEquals(1, result.getPageInfo().getSorting().size());
        assertEquals("Id", result.getPageInfo().getSorting().get(0).getColumnName());
        assertTrue((boolean)result.getPageInfo().getSorting().get(0).isAscending());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryScoreFilter() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", "rule|isNotEmpty|Name_with_underscore"),
                "operator", "lte",
                "right", Map.of("type", "literal", "value", "100")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void querySimpleFilterLessThanOnePage() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "test1")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertEquals(2, result.getRecords().get(0).getIdMapping().size());
        assertEquals("1234", result.getRecords().get(0).getIdMapping().get(c1.getName()+" / DataStudioAccount_hubspot"));
        assertEquals("454", result.getRecords().get(0).getIdMapping().get(c2.getName()+ " / DataStudioAccount_netsuite"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryBySyncariIdValidation() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", ExternalIdVisitor.DATASTUDIO_SYNCARI_ID),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "123")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        try {
            controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
            fail();
        } catch (Exception e) {
            assertEquals("Syncari Id should be a hex string", e.getMessage());
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryBySyncariIdFound() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value", ExternalIdVisitor.DATASTUDIO_SYNCARI_ID),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", e1.getSyncariEntityId())
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertEquals(e1.getSyncariEntityId(), result.getRecords().get(0).getSyncariId());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryBySyncariIdFoundNotFound() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", ExternalIdVisitor.DATASTUDIO_SYNCARI_ID),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "0f0bfe0000d0e00c00ec0000")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(0, result.getRecords().size());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryComplexFilterLessThanOnePage() throws Exception {
        var eq = List.of(
                Map.of("left",
                        Map.of("datatype", "double", "type", "variable", "value",
                                entityDef.getFieldByName("Name").getId()),
                        "operator", "eq", "right", Map.of("type", "literal", "value", "test2")),
                Map.of("left",
                        Map.of("datatype", "double", "type", "variable", "value",
                                entityDef.getFieldByName("Age").getId()),
                        "operator", "gt", "right", Map.of("type", "literal", "value", 5)));
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(Map.of("predicates", eq, "operator", "AND")).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void querySimpleFilterMoreThanOnePage() throws Exception {
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        // Case 1: Request 1 count, Query for first page and second page
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 1, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertTrue(result.getPageInfo().isHasMore());
        // Test next page
        String midCursor = result.getPageInfo().getEnd();
        result = controller.query(entityDef.getId(), midCursor, null, PageDirection.next.name(), 1, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertFalse(result.getPageInfo().isHasMore());
        // Test next page
        midCursor = result.getPageInfo().getEnd();
        result = controller.query(entityDef.getId(), result.getPageInfo().getEnd(), null, PageDirection.next.name(), 1, null, null, base64);
        assertEquals(0, result.getRecords().size());
        assertNull(result.getPageInfo().getStart());
        assertNull(result.getPageInfo().getEnd());
        assertFalse(result.getPageInfo().isHasMore());
        // Test previous page
        result = controller.query(entityDef.getId(), midCursor, null, PageDirection.previous.name(), 1, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertTrue(result.getPageInfo().isHasMore());

        // Case 2: Request 2 count, has only 1 page
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 2, null, null, base64);
        assertEquals(2, result.getRecords().size());
        assertEquals(result.getRecords().get(0).getSyncariId(), result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryPageNoFilter() throws Exception {
        // Case 1: Request 1 count, Query for first page and second page
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 2, null, null, null);
        assertEquals(2, result.getRecords().size());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertTrue(result.getPageInfo().isHasMore());
        // Test next page
        String midCursor = result.getPageInfo().getEnd();
        result = controller.query(entityDef.getId(), midCursor, null, PageDirection.next.name(), 2, null, null, null);
        assertEquals(2, result.getRecords().size());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertFalse(result.getPageInfo().isHasMore());
        // Test next page
        midCursor = result.getPageInfo().getEnd();
        result = controller.query(entityDef.getId(), result.getPageInfo().getEnd(), null, PageDirection.next.name(), 2, null, null, null);
        assertEquals(0, result.getRecords().size());
        assertNull(result.getPageInfo().getStart());
        assertNull(result.getPageInfo().getEnd());
        assertFalse(result.getPageInfo().isHasMore());
        // Test previous page
        result = controller.query(entityDef.getId(), midCursor, null, PageDirection.previous.name(), 2, null, null, null);
        assertEquals(2, result.getRecords().size());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertTrue(result.getPageInfo().isHasMore());

        // Case 2: Request 2 count, has only 1 page
        result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 4, null, null, null);
        assertEquals(4, result.getRecords().size());
        assertEquals(result.getRecords().get(0).getSyncariId(), result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void getBookmarkedFiltersNoResult() throws Exception {
        Map eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
                );
        DataFilterDTO filter = new DataFilterDTO().setName("filter1").setDescription("desc").setSyncariEntityId("123").setCriteria(eq);
        DataFilterDTO saved = controller.createFilter(filter);
        DataFilterResponse result = controller.getFilters(PageDirection.next.name(), 1, entityDef.getId(), null, true);
        assertEquals(0, result.getFilters().size());
        assertNull(result.getPageInfo().getEnd());
        assertNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
        controller.deleteFilter(saved.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void getBookmarkedFiltersHasResult() throws Exception {
        Map eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
                );
        DataFilterDTO filter = new DataFilterDTO().setName("filter1").setDescription("desc").setSyncariEntityId(entityDef.getId()).setCriteria(eq);
        DataFilterDTO saved = controller.createFilter(filter);
        controller.bookmark(saved.getId(), true);
        DataFilterResponse result = controller.getFilters(PageDirection.next.name(), 1, entityDef.getId(), null, true);
        assertEquals(1, result.getFilters().size());
        assertEquals(result.getPageInfo().getEnd(), result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
        controller.deleteFilter(saved.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void getFiltersNoResult() throws Exception {
        DataFilterResponse result = controller.getFilters(PageDirection.next.name(), 1, "123", null, false);
        assertEquals(0, result.getFilters().size());
        assertNull(result.getPageInfo().getEnd());
        assertNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void getFiltersWithResult() throws Exception {
        Map eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
                );
        DataFilterDTO filter = new DataFilterDTO().setName("filter1").setDescription("desc").setSyncariEntityId(entityDef.getId()).setCriteria(eq);
        DataFilterDTO saved = controller.createFilter(filter);
        DataFilterResponse result = controller.getFilters(PageDirection.next.name(), 1, entityDef.getId(), null, false);
        assertEquals(1, result.getFilters().size());
        assertEquals(result.getPageInfo().getEnd(), result.getPageInfo().getStart());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertFalse(result.getPageInfo().isHasMore());
        controller.deleteFilter(saved.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void createFilterValidations() throws Exception {
        Map eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
                );
        try {
            controller.createFilter(new DataFilterDTO());
            fail();
        } catch (Exception e) {
            assertEquals("Filter name is required", e.getMessage());
        }
        try {
            controller.createFilter(new DataFilterDTO().setName("filter1"));
            fail();
        } catch (Exception e) {
            assertEquals("Filter entity is required", e.getMessage());
        }
        try {
            controller.createFilter(new DataFilterDTO().setName("filter1").setSyncariEntityId("123"));
            fail();
        } catch (Exception e) {
            assertEquals("Filter criteria is required", e.getMessage());
        }
        try {
            DataFilterDTO saved = controller.createFilter(new DataFilterDTO().setName("filter1").setSyncariEntityId("123").setCriteria(eq));
            assertNotNull(saved.getId());
            controller.deleteFilter(saved.getId());
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void crudFilters() throws Exception {
        Map eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
                );
        DataFilterDTO filter = new DataFilterDTO().setName("filter1").setSyncariEntityId("123").setCriteria(eq);
        DataFilterDTO saved = controller.createFilter(filter);
        assertNotNull(saved.getId());
        DataFilterResponse list = controller.getFilters(PageDirection.next.name(), 1, "123", null, false);
        assertEquals(1, list.getFilters().size());
        assertEquals("filter1", list.getFilters().get(0).getName());
        assertEquals("123", list.getFilters().get(0).getSyncariEntityId());
        assertEquals(eq, list.getFilters().get(0).getCriteria());
        saved.setName("changed");
        Map changedCriteria = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 9)
                );
        saved.setCriteria(changedCriteria);
        controller.updateFilter(saved);
        list = controller.getFilters(PageDirection.next.name(), 2, "123", null, false);
        assertEquals(1, list.getFilters().size());
        assertEquals("changed", list.getFilters().get(0).getName());
        assertEquals("123", list.getFilters().get(0).getSyncariEntityId());
        assertEquals(changedCriteria, list.getFilters().get(0).getCriteria());
        saved.setBookmarked(true);
        controller.updateFilter(saved);
        list = controller.getFilters(PageDirection.next.name(), 2, "123", null, true);
        assertEquals(1, list.getFilters().size());
        assertEquals(saved.getId(), list.getFilters().get(0).getId());
        saved.setBookmarked(false);
        controller.updateFilter(saved);
        controller.deleteFilter(saved.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void externalIdsAddedToFields() throws Exception {
        mappingGraphService.initializeEntityGraph(entityDef, hubspotEntity);
        mappingGraphService.initializeEntityGraph(entityDef, netsuiteEntity);
        mappingGraphService.approveDraft(mappingGraphService.retrieveDraftEntityGraph(entityDef.getId()).get());
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Name").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "test1")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertEquals(2, result.getRecords().get(0).getIdMapping().size());
        assertEquals("1234", result.getRecords().get(0).getIdMapping().get(c1.getName()+" / "+hubspotEntity.getDisplayName()));
        assertEquals("454", result.getRecords().get(0).getIdMapping().get(c2.getName()+" / "+netsuiteEntity.getDisplayName()));

        // verify syncari id added
        assertTrue(result.getMetadata().getFields().containsKey("syncariId"));
        assertTrue(result.getMetadata().getFields().get("syncariId").get("fieldId").toString().startsWith("datastudio"));
        assertEquals("Syncari Id", result.getMetadata().getFields().get("syncariId").get("label").toString());
        assertTrue(result.getMetadata().getFields().get("syncariId").get("canFilter"));
        assertTrue(result.getMetadata().getFields().get("syncariId").get("canDisplay"));

        // verify hubspot id added
        String hubspotKey = c1.getId()+"_"+hubspotEntity.getId()+"_id";
        assertTrue(result.getMetadata().getFields().containsKey(hubspotKey));
        assertTrue(result.getMetadata().getFields().get(hubspotKey).get("fieldId").toString().startsWith("datastudio"));
        assertTrue(result.getMetadata().getFields().get(hubspotKey).get("canFilter"));
        assertFalse(result.getMetadata().getFields().get(hubspotKey).get("canDisplay"));
        assertEquals("Hubspot DataStudioAccount_hubspot Id", result.getMetadata().getFields().get(hubspotKey).get("label").toString());

        // verify netsuite id added
        String netsuiteKey = c2.getId()+"_"+netsuiteEntity.getId()+"_id";
        assertTrue(result.getMetadata().getFields().containsKey(netsuiteKey));
        assertTrue(result.getMetadata().getFields().get(netsuiteKey).get("fieldId").toString().startsWith("datastudio"));
        assertTrue(result.getMetadata().getFields().get(hubspotKey).get("canFilter"));
        assertFalse(result.getMetadata().getFields().get(hubspotKey).get("canDisplay"));
        assertEquals("Netsuite DataStudioAccount_netsuite Id", result.getMetadata().getFields().get(netsuiteKey).get("label").toString());

        // validate only 1 external id can be added to filter criteria
        var exp = List.of(
                Map.of("left",
                        Map.of("datatype", "string", "type", "variable", "value",
                                result.getMetadata().getFields().get(hubspotKey).get("fieldId").toString()),
                        "operator", "eq", "right", Map.of("type", "literal", "value", "test2")),
                Map.of("left",
                        Map.of("datatype", "string", "type", "variable", "value",
                                result.getMetadata().getFields().get(netsuiteKey).get("fieldId").toString()),
                        "operator", "eq", "right", Map.of("type", "literal", "value", "test3")));
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(Map.of("predicates", exp, "operator", "AND")).getBytes());
        try {
            controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        } catch (Exception e) {
            assertEquals("Data filter can have only 1 external id in the criteria", e.getMessage());
        }

        // validate both external id and syncari id cannot be present
        var exp1 = List.of(
                Map.of("left",
                        Map.of("datatype", "string", "type", "variable", "value",
                                result.getMetadata().getFields().get(hubspotKey).get("fieldId").toString()),
                        "operator", "eq", "right", Map.of("type", "literal", "value", "test2")),
                Map.of("left",
                        Map.of("datatype", "string", "type", "variable", "value", "syncariId"),
                        "operator", "eq", "right", Map.of("type", "literal", "value", "test3")));
        base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(Map.of("predicates", exp1, "operator", "AND")).getBytes());
        try {
            controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        } catch (Exception e) {
            assertEquals("Can have only Syncari Id or External Id in filters, not both", e.getMessage());
        }

        mappingGraphService.deleteApprovedEntityGraph(entityDef.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryByExternalIdFound() throws Exception {
        String hubspotKey = "datastudio_"+c1.getId()+"_"+hubspotEntity.getId()+"_id";
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", hubspotKey),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "1234")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());
        assertEquals(result.getPageInfo().getStart(), result.getPageInfo().getEnd());
        assertEquals(2, result.getRecords().get(0).getIdMapping().size());
        assertEquals("1234", result.getRecords().get(0).getIdMapping().get(c1.getName()+" / DataStudioAccount_hubspot"));
        assertEquals("454", result.getRecords().get(0).getIdMapping().get(c2.getName()+ " / DataStudioAccount_netsuite"));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void queryByExternalIdNotFound() throws Exception {
        String hubspotKey = "datastudio_"+c1.getId()+"_"+hubspotEntity.getId()+"_id";
        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", hubspotKey),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "123499")
                );
        String base64 = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 3, null, null, base64);
        assertEquals(0, result.getRecords().size());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void updateRecord() throws Exception {
        EntityData input = new EntityData();
        input.setName(DATA_STUDIO_ACCOUNT);
        input.addValue("Name", "record for update");
        input.addValue("Website", "update.com");
        input.addValue("Age", 5);
        input = entityRepo.save(entityDef,input);

        input.addValue("Name", "record for update changed");
        input.addValue("Age", 6);
        input.remove("_id");

        controller.update(entityDef.getId(), input.getId(), transformer.toEntityRecord(input));
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 5, null, null, null);
        assertEquals(5, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());

        String id = input.getId();
        assertTrue(expectedColumns < result.getMetadata().getFields().size());
        EntityRecord record = result.getRecords().stream().filter(r -> r.getSyncariId().equalsIgnoreCase(id)).findFirst().get();
        assertEquals("record for update changed", record.getValues().get("Name"));
        assertEquals("update.com", record.getValues().get("Website"));
        assertEquals(6L, record.getValues().get("Age"));
        assertEquals(1, result.getPageInfo().getSorting().size());
        assertEquals("Id", result.getPageInfo().getSorting().get(0).getColumnName());
        assertTrue((boolean)result.getPageInfo().getSorting().get(0).isAscending());
        entityRepo.deleteAll(entityDef, List.of(input));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void updateRecordValidations() throws Exception {
        EntityData input = new EntityData();
        input.setName("validation");
        input.addValue("Name", "record for validate");
        input = entityRepo.save(entityDef,input);

        input.addValue("Name", null);   // empty value for required field
        input.addValue("Website", "readonly");  // set value for readonly field
        input.addValue("Age", "invalid integer");   // invalid integer value
        input.addValue("Dob", "invalid date");      // invalid date value
        input.addValue("Contacted", "invalid datetime");    // invalid datetime value
        input.addValue("active", "invalid boolean");    // invalid boolean value
        input.addValue("longstr", "this value is tooooooo long");    // invalid string value

        input.remove("_id");

        EntityDataResponse response = controller.update(updateEntity.getId(), input.getId(), transformer.toEntityRecord(input)).getBody();

        // Should have 5 field errors total
        assertEquals(5, response.getErrors().getFields().size());
        assertEquals(0, response.getErrors().getRecord().size());

        // Check required field error for Name
        assertEquals(1, response.getErrors().getFields().get("Name").size());
        assertEquals("REQUIRED_FIELD", response.getErrors().getFields().get("Name").get(0).getCode());
        assertEquals("This field is required", response.getErrors().getFields().get("Name").get(0).getMessage());

        // Check type errors
        assertEquals(1, response.getErrors().getFields().get("Age").size());
        assertEquals("INVALID_TYPE", response.getErrors().getFields().get("Age").get(0).getCode());
        assertEquals("This value is invalid. Please Enter a valid Integer type", response.getErrors().getFields().get("Age").get(0).getMessage());

        assertEquals(1, response.getErrors().getFields().get("Dob").size());
        assertEquals("INVALID_TYPE", response.getErrors().getFields().get("Dob").get(0).getCode());
        assertEquals("This value is invalid. Please Enter a valid Date type", response.getErrors().getFields().get("Dob").get(0).getMessage());

        assertEquals(1, response.getErrors().getFields().get("Contacted").size());
        assertEquals("INVALID_TYPE", response.getErrors().getFields().get("Contacted").get(0).getCode());
        assertEquals("This value is invalid. Please Enter a valid Datetime type", response.getErrors().getFields().get("Contacted").get(0).getMessage());

        // Check length exceeded error
        assertEquals(1, response.getErrors().getFields().get("longstr").size());
        assertEquals("LENGTH_EXCEEDED", response.getErrors().getFields().get("longstr").get(0).getCode());
        assertEquals("This value exceeds maximum length of 10 characters", response.getErrors().getFields().get("longstr").get(0).getMessage());

        // Boolean values - active field should be converted to false (not an error)
        assertFalse(Boolean.parseBoolean(response.getRecord().getValues().get("active").toString()));
        entityRepo.deleteAll(entityDef, List.of(input));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void updateRecordNullValues() throws Exception {
        EntityData input = new EntityData();
        input.setName("validation");
        input.addValue("Name", "record for validate");
        input.addValue("Age", 10);
        input = entityRepo.save(entityDef,input);

        input.addValue("Age", null);
        input.remove("_id");

        EntityDataResponse update = controller.update(updateEntity.getId(), input.getId(), transformer.toEntityRecord(input)).getBody();
        assertTrue(update.getErrors().isEmpty());
        assertNull(update.getRecord().getValues().get("Age"));
        entityRepo.deleteAll(entityDef, List.of(input));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void getRecordDetail() {
        var result = controller.recordDetail(entityDef.getId(), e1.getId());

        assertNotNull(result.get("metadata"));
        assertNotNull(result.get("record"));

        EntityData record = result.get("record");
        assertEquals(e1.getId(), record.getId());
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void getMissingRecordDetail() throws Exception {
        try {
            var result = controller.recordDetail(entityDef.getId(), "609df37e504aa3e788ac0cf8");

            assertNotNull(result.get("metadata"));
            assertNotNull(result.get("record"));

            EntityData record = result.get("record");
            assertEquals(e1.getId(), record.getId());
        } catch (Exception err) {
            assertEquals(err.getClass(), NotFoundException.class);
            assertEquals(err.getMessage(), i18n("generic_record_not_found"));
        }
    }


    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void deleteRecord() throws Exception {
        EntityData input = new EntityData();
        input.setName(DATA_STUDIO_ACCOUNT);
        input.addValue("Name", "record for update");
        input.addValue("Website", "update.com");
        input.addValue("Age", 5);
        input = entityRepo.save(entityDef,input);

        input.addValue("Name", "record for update changed");
        input.addValue("Age", 6);

        controller.delete(entityDef.getId(), input.getId(), true);
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 5, null, null, null);
        assertEquals(5, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());

        String id = input.getId();
        Optional<EntityRecord> record = result.getRecords().stream().filter(r -> r.getSyncariId().equalsIgnoreCase(id)).findFirst();
        assertTrue(record.isPresent());
        assertTrue(record.get().isDeleted());
        entityRepo.deleteAll(entityDef, List.of(input));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void hardDeleteRecord() throws Exception {
        EntityData input = new EntityData();
        input.setName(DATA_STUDIO_ACCOUNT);
        input.addValue("Name", "record for update");
        input.addValue("Website", "update.com");
        input.addValue("Age", 5);
        input = entityRepo.save(entityDef,input);

        input.addValue("Name", "record for update changed");
        input.addValue("Age", 6);

        controller.delete(entityDef.getId(), input.getId(), false);
        DataQueryResponse result = controller.query(entityDef.getId(), null, null, PageDirection.next.name(), 5, null, null, null);
        assertEquals(4, result.getRecords().size());
        assertNotNull(result.getPageInfo().getEnd());
        assertNotNull(result.getPageInfo().getStart());

        String id = input.getId();
        Optional<EntityRecord> record = result.getRecords().stream().filter(r -> r.getSyncariId().equalsIgnoreCase(id)).findFirst();
        assertTrue(record.isEmpty());
        entityRepo.deleteAll(entityDef, List.of(input));
    }

    private void createRecords() {
        EntityData input = new EntityData();
        input.setName(DATA_STUDIO_ACCOUNT);
        input.addValue("Name", "test1");
        input.addValue("Website", "test1.com");
        input.addValue("Age", 5);
        input.addValue("Name_with_underscore", "test3");
        input.addValue("City", "弗里蒙特");
        EntityScore syncariScore = new EntityScore();
        FieldScore fScore = new FieldScore();
        fScore.addByRule("isNotEmpty", 10);
		syncariScore.addFieldScore("Name_with_underscore", fScore);
		input.setSyncariScore(syncariScore);
        e1 = entityRepo.save(entityDef,input);
        IdMapping mapping = new IdMapping().setSyncariId(e1.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "1234", hubspotEntity.getId())
                .addMapping(c2.getId(), "454", netsuiteEntity.getId());
        mappingRepo.save(mapping);
        EntityData input1 = new EntityData();
        input1.setName(DATA_STUDIO_ACCOUNT);
        input1.addValue("Name", "test2");
        input1.addValue("Website", "test2.com");
        input1.addValue("Age", 10);
        input1.addValue("City", "班加罗尔");
        input1.addValue("LastModified", new DateUtil().parse("2020-12-15T20:35:54.828Z", DateUtil.dateFormatMillis));
        e2 = entityRepo.save(entityDef,input1);
        mapping = new IdMapping().setSyncariId(e2.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "2345", hubspotEntity.getId())
                .addMapping(c2.getId(), "56546", netsuiteEntity.getId());
        mappingRepo.save(mapping);
        EntityData input2 = new EntityData();
        input2.setName(DATA_STUDIO_ACCOUNT);
        input2.addValue("Name", "Test3 Name");
        input2.addValue("Website", "test3.com");
        input2.addValue("City", "圣荷西");
        e3 = entityRepo.save(entityDef, input2);
        mapping = new IdMapping().setSyncariId(e3.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "4545", hubspotEntity.getId())
                .addMapping(c2.getId(), "678", netsuiteEntity.getId());
        mappingRepo.save(mapping);
        EntityData input3 = new EntityData();
        input3.setName(DATA_STUDIO_ACCOUNT);
        input3.addValue("Name", "test4 with space");
        input3.addValue("Website", "test4.com");
        input3.addValue("Age", 5);
        input3.addValue("City", "San Francisco");
        e4 = entityRepo.save(entityDef,input3);
        mapping = new IdMapping().setSyncariId(e4.getSyncariEntityId())
                .setEntityName(DATA_STUDIO_ACCOUNT)
                .addMapping(c1.getId(), "86767", hubspotEntity.getId())
                .addMapping(c2.getId(), "787", netsuiteEntity.getId());
        mappingRepo.save(mapping);
    }

    private EntityDefinition getEntity() {
        EntityDefinition sourceEntity = new EntityDefinition(DATA_STUDIO_ACCOUNT, DATA_STUDIO_ACCOUNT);
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(connectorService.getSyncariConnector().getId());
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition longName = new AttributeDefinition().setApiName("Name_with_underscore")
        		.setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        longName.setDraftStatus(DraftStatus.APPROVED);
        longName.setStatus(Status.ACTIVE);
        longName = attributeProxyRepo.save(longName);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attributeProxyRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attributeProxyRepo.save(age);
        AttributeDefinition modified = new AttributeDefinition().setApiName("LastModified")
                .setDataType(new DatetimeType()).setDisplayName("LastModified").setEntityId(sourceEntity.getId());
        modified.setDraftStatus(DraftStatus.APPROVED);
        modified.setStatus(Status.ACTIVE);
        modified = attributeProxyRepo.save(modified);
        AttributeDefinition city = new AttributeDefinition().setApiName("City")
                .setDataType(new StringType()).setDisplayName("City").setEntityId(sourceEntity.getId());
        city.setDraftStatus(DraftStatus.APPROVED);
        city.setStatus(Status.ACTIVE);
        city = attributeProxyRepo.save(city);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        sourceEntity.addField(city);
        sourceEntity.addField(modified);
        return sourceEntity;
    }

    private EntityDefinition getHubspotEntity() {
        EntityDefinition sourceEntity = new EntityDefinition(DATA_STUDIO_ACCOUNT+"_hubspot", DATA_STUDIO_ACCOUNT+"_hubspot");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(c1.getId());
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition id = new AttributeDefinition().setApiName("Id")
                .setDataType(new IntegerType()).setDisplayName("Id").setEntityId(sourceEntity.getId());
        id.setDraftStatus(DraftStatus.APPROVED);
        id.setStatus(Status.ACTIVE);
        id.setIdField(true);
        id = attributeProxyRepo.save(id);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attributeProxyRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attributeProxyRepo.save(age);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        return sourceEntity;
    }

    private EntityDefinition getNetsuiteEntity() {
        EntityDefinition sourceEntity = new EntityDefinition(DATA_STUDIO_ACCOUNT+"_netsuite", DATA_STUDIO_ACCOUNT+"_netsuite");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(c2.getId());
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition id = new AttributeDefinition().setApiName("Id")
                .setDataType(new StringType()).setDisplayName("Id").setEntityId(sourceEntity.getId());
        id.setDraftStatus(DraftStatus.APPROVED);
        id.setStatus(Status.ACTIVE);
        id.setIdField(true);
        id = attributeProxyRepo.save(id);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeProxyRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website = attributeProxyRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attributeProxyRepo.save(age);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        return sourceEntity;
    }

    private EntityDefinition getEntityForValidation() {
        EntityDefinition sourceEntity = new EntityDefinition("validation", "validation");
        sourceEntity.setDraftStatus(DraftStatus.APPROVED);
        sourceEntity.setConnectorId(connectorService.getSyncariConnector().getId());
        sourceEntity = entityProxyRepo.save(sourceEntity);
        AttributeDefinition name = new AttributeDefinition().setApiName("Name")
                .setDataType(new StringType()).setDisplayName("Name").setEntityId(sourceEntity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name.setNillable(false);
        name = attributeProxyRepo.save(name);
        AttributeDefinition website = new AttributeDefinition().setApiName("Website")
                .setDataType(new StringType()).setDisplayName("Website").setEntityId(sourceEntity.getId());
        website.setDraftStatus(DraftStatus.APPROVED);
        website.setStatus(Status.ACTIVE);
        website.setUpdatable(false);
        website = attributeProxyRepo.save(website);
        AttributeDefinition age = new AttributeDefinition().setApiName("Age")
                .setDataType(new IntegerType()).setDisplayName("Age").setEntityId(sourceEntity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attributeProxyRepo.save(age);
        AttributeDefinition dob = new AttributeDefinition().setApiName("Dob")
                .setDataType(new DateType()).setDisplayName("Dob").setEntityId(sourceEntity.getId());
        dob.setDraftStatus(DraftStatus.APPROVED);
        dob.setStatus(Status.ACTIVE);
        dob = attributeProxyRepo.save(dob);
        AttributeDefinition contacted = new AttributeDefinition().setApiName("Contacted")
                .setDataType(new DatetimeType()).setDisplayName("Contacted").setEntityId(sourceEntity.getId());
        contacted.setDraftStatus(DraftStatus.APPROVED);
        contacted.setStatus(Status.ACTIVE);
        contacted = attributeProxyRepo.save(contacted);
        AttributeDefinition active = new AttributeDefinition().setApiName("active")
                .setDataType(new BooleanType()).setDisplayName("active").setEntityId(sourceEntity.getId());
        active.setDraftStatus(DraftStatus.APPROVED);
        active.setStatus(Status.ACTIVE);
        active = attributeProxyRepo.save(active);
        AttributeDefinition longstr = new AttributeDefinition().setApiName("longstr")
                .setDataType(new StringType()).setDisplayName("longstr").setEntityId(sourceEntity.getId());
        longstr.setDraftStatus(DraftStatus.APPROVED);
        longstr.setStatus(Status.ACTIVE);
        longstr.setLength(10);
        longstr = attributeProxyRepo.save(longstr);
        sourceEntity.addField(name);
        sourceEntity.addField(website);
        sourceEntity.addField(age);
        return sourceEntity;
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void batchDeleteRecords() throws Exception {
        EntityData input1 = new EntityData();
        input1.setName(DATA_STUDIO_ACCOUNT);
        input1.addValue("Name", "record for deletion1");
        input1.addValue("Website", "delete1.com");
        input1.addValue("Age", 5);

        EntityData input2 = new EntityData();
        input2.setName(DATA_STUDIO_ACCOUNT);
        input2.addValue("Name", "record for deletion2");
        input2.addValue("Website", "delete2.com");
        input2.addValue("Age", 5);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
        );
        String encodedEq = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        List<EntityData> entities = entityRepo.saveAll(entityDef,List.of(input1, input2));

        try {
            var response = controller.batchDelete(entityDef.getId(), false, Map.of("predicate", encodedEq));
            // does not really do any validation, as the operation is async
            assertEquals("delete", response.get("operation"));
        } finally {
            entityRepo.deleteAll(entityDef, entities);
        }

    }
    
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void batchPurgeRecords() throws Exception {
        EntityData input1 = new EntityData();
        input1.setName(DATA_STUDIO_ACCOUNT);
        input1.addValue("Name", "record for deletion1");
        input1.addValue("Website", "delete1.com");
        input1.addValue("Age", 5);

        EntityData input2 = new EntityData();
        input2.setName(DATA_STUDIO_ACCOUNT);
        input2.addValue("Name", "record for deletion2");
        input2.addValue("Website", "delete2.com");
        input2.addValue("Age", 5);
        
        List<EntityData> entities = entityRepo.saveAll(entityDef,List.of(input1, input2));

        try {
            var response = controller.batchDelete(entityDef.getId(), false, Map.of("predicate", ""));
            // does not really do any validation, as the operation is async
            assertEquals("purge", response.get("operation"));
        } finally {
            entityRepo.deleteAll(entityDef, entities);
        }

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO, WRITE_DATA_STUDIO})
    public void batchUpdateRecords() throws Exception {
        EntityData input1 = new EntityData();
        input1.setName(DATA_STUDIO_ACCOUNT);
        input1.addValue("Name", "record for update1");
        input1.addValue("Website", "update1.com");
        input1.addValue("Age", 5);

        EntityData input2 = new EntityData();
        input2.setName(DATA_STUDIO_ACCOUNT);
        input2.addValue("Name", "record for update2");
        input2.addValue("Website", "update2.com");
        input2.addValue("Age", 5);

        var eq = Map.of(
                "left", Map.of("datatype", "double", "type", "variable", "value", entityDef.getFieldByName("Age").getId()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", 5)
        );

        var updates = Map.of("Website", "update.com");

        String encodedEq = DatatypeConverter.printBase64Binary(mapper.writeValueAsString(eq).getBytes());

        List<EntityData> entities = entityRepo.saveAll(entityDef,List.of(input1, input2));

        try {
            KeyValue kv = new KeyValue().set("predicate", encodedEq).set("updates", updates);
            var response = controller.batchUpdate(entityDef.getId(), kv);
            assertEquals("update", response.get("operation"));
        } finally {
             entityRepo.deleteAll(entityDef, entities);
        }
    }
    
    public void downloadFileTypeRecord() throws Exception {

        // Dump the file into inmemory storage
        String fileURL = "src/test/resources/documents/sample.pdf";
        String fileID = "syncari_tests/arcade/sample.pdf";
        try (InputStream fs = new FileInputStream(fileURL)) {
            gcsFileManager.write(fs, fileID);
        } catch (IOException e) {
            log.error("Fail to load file into GCSFileManager. ", e);
            fail();
        }

        List<EntityData> eds = new ArrayList<>();

        try {
            EntityData input = new EntityData();
            input.setName(DATA_STUDIO_ACCOUNT);
            input.addValue("name", "sample.pdf");
            input.addValue("fileType", "pdf");
            input.addValue(EntityData.SYNCARI_FILE_LINK_FIELD_NAME, fileID);
            input = entityRepo.save(entityDef, input);
            eds.add(input);

            // Download the file via the API.
            ResponseEntity<Resource> downloadedFile = controller.downloadFileTypeRecord(entityDef.getId(), input.getId());
            assertNotNull(downloadedFile);
            assertTrue(downloadedFile.getBody().exists());
            assertTrue(downloadedFile.getBody() instanceof InputStreamResource);
            assertEquals("attachment", downloadedFile.getHeaders().getContentDisposition().getType());
            assertEquals("sample.pdf", downloadedFile.getHeaders().getContentDisposition().getFilename());
            assertEquals(MediaType.PDF.toString(), downloadedFile.getHeaders().getContentType().toString());
        } finally {
            entityRepo.deleteAll(entityDef, eds);
            gcsFileManager.delete(fileID);
        }
    }
}
