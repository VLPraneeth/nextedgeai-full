package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.studio.DataQueryResponse;
import com.syncari.connector.EntityData;
import com.syncari.core.datatype.*;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_DATA_STUDIO;
import static org.junit.Assert.*;

/**
 * Test class for Data Studio sort functionality
 * Tests all positive and negative cases for orderBy and sortDirection parameters
 */
@Slf4j
public class DatastudioSortTest extends AbstractSyncariTest {
    private static final String TEST_ENTITY_NAME = "SortTestEntity";

    @Autowired
    private DatastudioController controller;
    @Autowired
    SchemaService schemaService;
    ObjectMapper mapper = new ObjectMapper();
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    AttributeRepo attributeRepo;
    @Autowired
    EntityDefinitionRepo entityDefinitionRepo;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ConnectorMetadataService metaService;
    @Autowired
    ObjectTransformer transformer;

    static EntityDefinition testEntity;
    static List<EntityData> testRecords;

    @Override
    public void setUp() {
        super.setUp();
        if (testEntity == null) {
            testEntity = createTestEntity();
            testRecords = createTestData();
        }
    }

    @Override
    public void tearDown() {
        // Cleanup will be done manually in tests
    }

    /**
     * Test: Sort by string field in both directions
     * Expected: ASC = A-Z, DESC = Z-A
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortByString() throws Exception {
        // Test ascending
        DataQueryResponse resultAsc = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Name", "asc", null);
        assertEquals(5, resultAsc.getRecords().size());
        List<String> namesAsc = resultAsc.getRecords().stream().map(r -> (String) r.getValues().get("Name")).collect(Collectors.toList());
        assertEquals("Alice", namesAsc.get(0));
        assertEquals("Eve", namesAsc.get(4));

        // Test descending
        DataQueryResponse resultDesc = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Name", "desc", null);
        List<String> namesDesc = resultDesc.getRecords().stream().map(r -> (String) r.getValues().get("Name")).collect(Collectors.toList());
        assertEquals("Eve", namesDesc.get(0));
        assertEquals("Alice", namesDesc.get(4));
    }

    /**
     * Test: Sort by integer and double fields in both directions
     * Expected: Ascending = low to high, Descending = high to low
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortByNumericFields() throws Exception {
        // Test integer ascending
        DataQueryResponse resultAsc = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Age", "asc", null);
        List<Long> agesAsc = resultAsc.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
        assertEquals(Long.valueOf(25), agesAsc.get(0));
        assertEquals(Long.valueOf(45), agesAsc.get(4));

        // Test integer descending
        DataQueryResponse resultDesc = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Age", "desc", null);
        List<Long> agesDesc = resultDesc.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
        assertEquals(Long.valueOf(45), agesDesc.get(0));
        assertEquals(Long.valueOf(25), agesDesc.get(4));

        // Test double ascending
        DataQueryResponse resultSalary = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Salary", "asc", null);
        List<Double> salaries = resultSalary.getRecords().stream().map(r -> (Double) r.getValues().get("Salary")).collect(Collectors.toList());
        assertEquals(Double.valueOf(50000.0), salaries.get(0));
        assertEquals(Double.valueOf(90000.0), salaries.get(4));
    }

    /**
     * Test: Sort parameter variations and edge cases
     * Expected: Default direction = DESC (latest first), invalid direction = DESC, explicit ASC/DESC works
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortParameterVariations() throws Exception {
        // Test default direction (null) = descending (to show latest)
        DataQueryResponse resultDefault = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Age", null, null);
        List<Long> agesDefault = resultDefault.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
        assertEquals(Long.valueOf(45), agesDefault.get(0));

        // Test no sort parameter (null orderBy) - uses default _id sort
        DataQueryResponse resultNoSort = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, null, null, null);
        assertEquals(5, resultNoSort.getRecords().size());
        assertNotNull(resultNoSort.getPageInfo());

        // Test explicit ASC (case insensitive)
        DataQueryResponse resultUpperAsc = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Age", "ASC", null);
        assertEquals(Long.valueOf(25), resultUpperAsc.getRecords().get(0).getValues().get("Age"));

        // Test explicit DESC (case insensitive)
        DataQueryResponse resultUpperDesc = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Age", "DESC", null);
        assertEquals(Long.valueOf(45), resultUpperDesc.getRecords().get(0).getValues().get("Age"));

        // Test invalid direction = treated as descending (default)
        DataQueryResponse resultInvalid = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "Age", "invalid", null);
        assertEquals(Long.valueOf(45), resultInvalid.getRecords().get(0).getValues().get("Age"));
    }

    /**
     * Test: Invalid field name for orderBy
     * Expected: Should throw SyncariValidationException
     */
    @Test(expected = SyncariValidationException.class)
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortByInvalidField() throws Exception {
        controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 10, "NonExistentField", "asc", null);
    }

    /**
     * Test: Sort with pagination (offset-based with page numbers)
     * Expected: Pagination works correctly with sorting using page numbers
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortWithPagination() throws Exception {
        // First page (page=1) - get 2 records sorted by Age ascending
        DataQueryResponse page1 = controller.query(testEntity.getId(), null, 1, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(2, page1.getRecords().size());
        assertEquals(Long.valueOf(25), page1.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(30), page1.getRecords().get(1).getValues().get("Age"));
        assertTrue(page1.getPageInfo().isHasMore());
        assertEquals(1, page1.getPageInfo().getPageNumber());

        // Second page (page=2) forward
        DataQueryResponse page2 = controller.query(testEntity.getId(), null, 2, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(2, page2.getRecords().size());
        assertEquals(Long.valueOf(35), page2.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(40), page2.getRecords().get(1).getValues().get("Age"));
        assertTrue(page2.getPageInfo().isHasMore());
        assertEquals(2, page2.getPageInfo().getPageNumber());

        // Third page (page=3)
        DataQueryResponse page3 = controller.query(testEntity.getId(), null, 3, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(1, page3.getRecords().size());
        assertEquals(Long.valueOf(45), page3.getRecords().get(0).getValues().get("Age"));
        assertFalse(page3.getPageInfo().isHasMore());
        assertTrue(page3.getPageInfo().isHasPrevious());
        assertEquals(3, page3.getPageInfo().getPageNumber());
    }

    /**
     * Test: Sort with null values
     * Expected: Null values handling in MongoDB
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortWithNullValues() throws Exception {
        // Add records with null Age values
        EntityData nullRecord1 = new EntityData();
        nullRecord1.setName(TEST_ENTITY_NAME);
        nullRecord1.addValue("Name", "Zack");
        nullRecord1.addValue("Salary", 55000.0);
        nullRecord1 = entityRepo.save(testEntity, nullRecord1);

        EntityData nullRecord2 = new EntityData();
        nullRecord2.setName(TEST_ENTITY_NAME);
        nullRecord2.addValue("Name", "Amy");
        nullRecord2.addValue("Salary", 65000.0);
        nullRecord2 = entityRepo.save(testEntity, nullRecord2);

        try {
            DataQueryResponse result = controller.query(
                testEntity.getId(),
                null,
                null,
                PageDirection.next.name(),
                10,
                "Age",
                "asc",
                null
            );

            assertEquals(7, result.getRecords().size());

            // MongoDB sorts nulls before non-null values in ascending order
            // Just verify we got all records including null ones
            long nullCount = result.getRecords().stream()
                .filter(r -> r.getValues().get("Age") == null)
                .count();
            assertEquals(2, nullCount);
        } finally {
            entityRepo.deleteAll(testEntity, Arrays.asList(nullRecord1, nullRecord2));
        }
    }


    /**
     * Test: Empty result set with sort
     * Expected: Should return empty result set without errors
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testSortEmptyResultSet() throws Exception {
        // Create entity with no records
        EntityDefinition emptyEntity = createEmptyEntity();

        try {
            DataQueryResponse result = controller.query(
                emptyEntity.getId(),
                null,
                null,
                PageDirection.next.name(),
                10,
                "Name",
                "asc",
                null
            );

            assertEquals(0, result.getRecords().size());
            assertNull(result.getPageInfo().getStart());
            assertNull(result.getPageInfo().getEnd());
            assertFalse(result.getPageInfo().isHasMore());
        } finally {
            entityDefinitionRepo.delete(emptyEntity);
        }
    }

    /**
     * Test: No custom sort with multiple pages - cursor pagination
     * Expected: Uses cursor-based pagination (_id sort), all records in insertion order across pages
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testNoCustomSortWithMultiplePages() throws Exception {
        // Add more records to have 3+ pages (currently have 5, add 5 more = 10 total)
        List<EntityData> newRecords = new ArrayList<>();
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Frank").addValue("Age", 50).addValue("Salary", 95000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Grace").addValue("Age", 28).addValue("Salary", 75000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Henry").addValue("Age", 33).addValue("Salary", 68000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Iris").addValue("Age", 29).addValue("Salary", 72000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Jack").addValue("Age", 38).addValue("Salary", 88000.0));

        List<EntityData> savedRecords = new ArrayList<>();
        for (EntityData record : newRecords) {
            savedRecords.add(entityRepo.save(testEntity, record));
        }

        try {
            // Page 1 (3 records, no custom sort - uses cursor pagination)
            DataQueryResponse page1 = controller.query(testEntity.getId(), null, null, PageDirection.next.name(), 3, null, null, null);
            assertEquals(3, page1.getRecords().size());
            assertTrue(page1.getPageInfo().isHasMore());
            assertNotNull(page1.getPageInfo().getEnd());
            List<String> page1Ids = page1.getRecords().stream().map(r -> r.getSyncariId()).collect(Collectors.toList());

            // Page 2 (using cursor from page 1)
            DataQueryResponse page2 = controller.query(testEntity.getId(), page1.getPageInfo().getEnd(), null, PageDirection.next.name(), 3, null, null, null);
            assertEquals(3, page2.getRecords().size());
            assertTrue(page2.getPageInfo().isHasMore());
            assertNotNull(page2.getPageInfo().getEnd());
            List<String> page2Ids = page2.getRecords().stream().map(r -> r.getSyncariId()).collect(Collectors.toList());

            // Page 3 (using cursor from page 2)
            DataQueryResponse page3 = controller.query(testEntity.getId(), page2.getPageInfo().getEnd(), null, PageDirection.next.name(), 3, null, null, null);
            assertEquals(3, page3.getRecords().size());
            assertTrue(page3.getPageInfo().isHasMore());
            List<String> page3Ids = page3.getRecords().stream().map(r -> r.getSyncariId()).collect(Collectors.toList());

            // Page 4 (last page)
            DataQueryResponse page4 = controller.query(testEntity.getId(), page3.getPageInfo().getEnd(), null, PageDirection.next.name(), 3, null, null, null);
            assertEquals(1, page4.getRecords().size());
            assertFalse(page4.getPageInfo().isHasMore());

            // Verify no duplicate IDs across pages
            Set<String> allIds = new HashSet<>();
            allIds.addAll(page1Ids);
            allIds.addAll(page2Ids);
            allIds.addAll(page3Ids);
            assertEquals(9, allIds.size()); // Should have 9 unique IDs from first 3 pages

        } finally {
            entityRepo.deleteAll(testEntity, savedRecords);
        }
    }

    /**
     * Test: Custom sort ASC with multiple pages - verify all content across pages
     * Expected: All records sorted by Age ascending, split across pages correctly
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testCustomSortAscWithMultiplePagesFullContent() throws Exception {
        // Add more records (currently 5: ages 25,30,35,40,45; add 5 more)
        List<EntityData> newRecords = new ArrayList<>();
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Frank").addValue("Age", 22).addValue("Salary", 45000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Grace").addValue("Age", 28).addValue("Salary", 55000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Henry").addValue("Age", 33).addValue("Salary", 68000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Iris").addValue("Age", 38).addValue("Salary", 72000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Jack").addValue("Age", 50).addValue("Salary", 95000.0));

        List<EntityData> savedRecords = new ArrayList<>();
        for (EntityData record : newRecords) {
            savedRecords.add(entityRepo.save(testEntity, record));
        }

        try {
            // Expected ages in ASC order: 22, 25, 28, 30, 33, 35, 38, 40, 45, 50

            // Page 1 (3 records)
            DataQueryResponse page1 = controller.query(testEntity.getId(), null, 1, PageDirection.next.name(), 3, "Age", "asc", null);
            assertEquals(3, page1.getRecords().size());
            List<Long> ages1 = page1.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
            assertEquals(Long.valueOf(22), ages1.get(0));
            assertEquals(Long.valueOf(25), ages1.get(1));
            assertEquals(Long.valueOf(28), ages1.get(2));
            assertTrue(page1.getPageInfo().isHasMore());
            assertFalse(page1.getPageInfo().isHasPrevious());

            // Page 2 (3 records)
            DataQueryResponse page2 = controller.query(testEntity.getId(), null, 2, PageDirection.next.name(), 3, "Age", "asc", null);
            assertEquals(3, page2.getRecords().size());
            List<Long> ages2 = page2.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
            assertEquals(Long.valueOf(30), ages2.get(0));
            assertEquals(Long.valueOf(33), ages2.get(1));
            assertEquals(Long.valueOf(35), ages2.get(2));
            assertTrue(page2.getPageInfo().isHasMore());
            assertTrue(page2.getPageInfo().isHasPrevious());

            // Page 3 (3 records)
            DataQueryResponse page3 = controller.query(testEntity.getId(), null, 3, PageDirection.next.name(), 3, "Age", "asc", null);
            assertEquals(3, page3.getRecords().size());
            List<Long> ages3 = page3.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
            assertEquals(Long.valueOf(38), ages3.get(0));
            assertEquals(Long.valueOf(40), ages3.get(1));
            assertEquals(Long.valueOf(45), ages3.get(2));
            assertTrue(page3.getPageInfo().isHasMore());
            assertTrue(page3.getPageInfo().isHasPrevious());

            // Page 4 (1 record - last page)
            DataQueryResponse page4 = controller.query(testEntity.getId(), null, 4, PageDirection.next.name(), 3, "Age", "asc", null);
            assertEquals(1, page4.getRecords().size());
            List<Long> ages4 = page4.getRecords().stream().map(r -> (Long) r.getValues().get("Age")).collect(Collectors.toList());
            assertEquals(Long.valueOf(50), ages4.get(0));
            assertFalse(page4.getPageInfo().isHasMore());
            assertTrue(page4.getPageInfo().isHasPrevious());

            // Verify cross-page ordering: last of page N < first of page N+1
            assertTrue(ages1.get(2) < ages2.get(0)); // 28 < 30
            assertTrue(ages2.get(2) < ages3.get(0)); // 35 < 38
            assertTrue(ages3.get(2) < ages4.get(0)); // 45 < 50

        } finally {
            entityRepo.deleteAll(testEntity, savedRecords);
        }
    }

    /**
     * Test: Custom sort DESC with multiple pages - verify all content across pages
     * Expected: All records sorted by Salary descending, split across pages correctly
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testCustomSortDescWithMultiplePagesFullContent() throws Exception {
        // Add more records (currently 5: salaries 50k,60k,70k,80k,90k; add 5 more)
        List<EntityData> newRecords = new ArrayList<>();
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Frank").addValue("Age", 22).addValue("Salary", 45000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Grace").addValue("Age", 28).addValue("Salary", 95000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Henry").addValue("Age", 33).addValue("Salary", 55000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Iris").addValue("Age", 38).addValue("Salary", 75000.0));
        newRecords.add(new EntityData().setName(TEST_ENTITY_NAME).addValue("Name", "Jack").addValue("Age", 42).addValue("Salary", 100000.0));

        List<EntityData> savedRecords = new ArrayList<>();
        for (EntityData record : newRecords) {
            savedRecords.add(entityRepo.save(testEntity, record));
        }

        try {
            // Expected salaries in DESC order: 100k, 95k, 90k, 80k, 75k, 70k, 60k, 55k, 50k, 45k

            // Page 1 (3 records)
            DataQueryResponse page0 = controller.query(testEntity.getId(), null, 1, PageDirection.next.name(), 3, "Salary", "desc", null);
            assertEquals(3, page0.getRecords().size());
            List<Double> salaries0 = page0.getRecords().stream().map(r -> (Double) r.getValues().get("Salary")).collect(Collectors.toList());
            assertEquals(Double.valueOf(100000.0), salaries0.get(0));
            assertEquals(Double.valueOf(95000.0), salaries0.get(1));
            assertEquals(Double.valueOf(90000.0), salaries0.get(2));
            assertTrue(page0.getPageInfo().isHasMore());

            // Page 2 (3 records)
            DataQueryResponse page1 = controller.query(testEntity.getId(), null, 2, PageDirection.next.name(), 3, "Salary", "desc", null);
            assertEquals(3, page1.getRecords().size());
            List<Double> salaries1 = page1.getRecords().stream().map(r -> (Double) r.getValues().get("Salary")).collect(Collectors.toList());
            assertEquals(Double.valueOf(80000.0), salaries1.get(0));
            assertEquals(Double.valueOf(75000.0), salaries1.get(1));
            assertEquals(Double.valueOf(70000.0), salaries1.get(2));
            assertTrue(page1.getPageInfo().isHasMore());

            // Page 3 (3 records)
            DataQueryResponse page2 = controller.query(testEntity.getId(), null, 3, PageDirection.next.name(), 3, "Salary", "desc", null);
            assertEquals(3, page2.getRecords().size());
            List<Double> salaries2 = page2.getRecords().stream().map(r -> (Double) r.getValues().get("Salary")).collect(Collectors.toList());
            assertEquals(Double.valueOf(60000.0), salaries2.get(0));
            assertEquals(Double.valueOf(55000.0), salaries2.get(1));
            assertEquals(Double.valueOf(50000.0), salaries2.get(2));
            assertTrue(page2.getPageInfo().isHasMore());

            // Page 4 (1 record - last page)
            DataQueryResponse page3 = controller.query(testEntity.getId(), null, 4, PageDirection.next.name(), 3, "Salary", "desc", null);
            assertEquals(1, page3.getRecords().size());
            List<Double> salaries3 = page3.getRecords().stream().map(r -> (Double) r.getValues().get("Salary")).collect(Collectors.toList());
            assertEquals(Double.valueOf(45000.0), salaries3.get(0));
            assertFalse(page3.getPageInfo().isHasMore());

            // Verify cross-page ordering: last of page N > first of page N+1 (DESC order)
            assertTrue(salaries0.get(2) > salaries1.get(0)); // 90k > 80k
            assertTrue(salaries1.get(2) > salaries2.get(0)); // 70k > 60k
            assertTrue(salaries2.get(2) > salaries3.get(0)); // 50k > 45k

            // Verify all values are in descending order within each page
            for (int i = 0; i < salaries0.size() - 1; i++) {
                assertTrue(salaries0.get(i) >= salaries0.get(i + 1));
            }
            for (int i = 0; i < salaries1.size() - 1; i++) {
                assertTrue(salaries1.get(i) >= salaries1.get(i + 1));
            }

        } finally {
            entityRepo.deleteAll(testEntity, savedRecords);
        }
    }

    /**
     * Test: Offset pagination with PREVIOUS direction and custom sort
     * This test verifies the bug fix where sort was incorrectly reversed for previous direction
     * Expected: Sort direction should remain consistent regardless of page direction
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testOffsetPaginationWithPreviousDirection() throws Exception {
        DataQueryResponse page2Next = controller.query(testEntity.getId(), null, 2, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(2, page2Next.getRecords().size());
        assertEquals(Long.valueOf(35), page2Next.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(40), page2Next.getRecords().get(1).getValues().get("Age"));

        DataQueryResponse page1Prev = controller.query(testEntity.getId(), null, 1, PageDirection.previous.name(), 2, "Age", "asc", null);
        assertEquals(2, page1Prev.getRecords().size());
        assertEquals(Long.valueOf(25), page1Prev.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(30), page1Prev.getRecords().get(1).getValues().get("Age"));

        DataQueryResponse page1Next = controller.query(testEntity.getId(), null, 1, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(page1Next.getRecords().get(0).getValues().get("Age"),
                     page1Prev.getRecords().get(0).getValues().get("Age"));
        assertEquals(page1Next.getRecords().get(1).getValues().get("Age"),
                     page1Prev.getRecords().get(1).getValues().get("Age"));
    }

    /**
     * Test: Offset pagination with PREVIOUS direction causing empty results
     * This test verifies the bug where reversed sort + offset could skip past all records
     * Expected: Should return records, not empty array
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testOffsetPaginationPreviousNoEmptyResults() throws Exception {
        DataQueryResponse page3Next = controller.query(testEntity.getId(), null, 3, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(1, page3Next.getRecords().size());
        assertEquals(Long.valueOf(45), page3Next.getRecords().get(0).getValues().get("Age"));

        DataQueryResponse page2Prev = controller.query(testEntity.getId(), null, 2, PageDirection.previous.name(), 2, "Age", "asc", null);
        assertEquals(2, page2Prev.getRecords().size());
        assertEquals(Long.valueOf(35), page2Prev.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(40), page2Prev.getRecords().get(1).getValues().get("Age"));

        assertFalse("Records should not be empty", page2Prev.getRecords().isEmpty());
    }

    /**
     * Test: Offset pagination DESC sort with PREVIOUS direction
     * Expected: DESC sort should remain DESC, not flip to ASC
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testOffsetPaginationPreviousWithDescSort() throws Exception {
        DataQueryResponse page2Next = controller.query(testEntity.getId(), null, 2, PageDirection.next.name(), 2, "Age", "desc", null);
        assertEquals(2, page2Next.getRecords().size());
        assertEquals(Long.valueOf(35), page2Next.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(30), page2Next.getRecords().get(1).getValues().get("Age"));

        DataQueryResponse page1Prev = controller.query(testEntity.getId(), null, 1, PageDirection.previous.name(), 2, "Age", "desc", null);
        assertEquals(2, page1Prev.getRecords().size());
        assertEquals(Long.valueOf(45), page1Prev.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(40), page1Prev.getRecords().get(1).getValues().get("Age"));

        assertTrue((Long)page1Prev.getRecords().get(0).getValues().get("Age") >
                   (Long)page1Prev.getRecords().get(1).getValues().get("Age"));
    }

    /**
     * Test: Multiple page navigation with mixed directions
     * Expected: Consistent results regardless of navigation direction
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testMixedDirectionNavigation() throws Exception {
        DataQueryResponse page1 = controller.query(testEntity.getId(), null, 1, PageDirection.next.name(), 2, "Age", "asc", null);
        List<Long> page1Ages = page1.getRecords().stream()
            .map(r -> (Long) r.getValues().get("Age"))
            .collect(Collectors.toList());

        DataQueryResponse page2 = controller.query(testEntity.getId(), null, 2, PageDirection.next.name(), 2, "Age", "asc", null);
        List<Long> page2Ages = page2.getRecords().stream()
            .map(r -> (Long) r.getValues().get("Age"))
            .collect(Collectors.toList());

        DataQueryResponse page1Back = controller.query(testEntity.getId(), null, 1, PageDirection.previous.name(), 2, "Age", "asc", null);
        List<Long> page1BackAges = page1Back.getRecords().stream()
            .map(r -> (Long) r.getValues().get("Age"))
            .collect(Collectors.toList());

        DataQueryResponse page2Back = controller.query(testEntity.getId(), null, 2, PageDirection.previous.name(), 2, "Age", "asc", null);
        List<Long> page2BackAges = page2Back.getRecords().stream()
            .map(r -> (Long) r.getValues().get("Age"))
            .collect(Collectors.toList());

        assertEquals("Page 1 should be same for both directions", page1Ages, page1BackAges);
        assertEquals("Page 2 should be same for both directions", page2Ages, page2BackAges);

        assertEquals(Long.valueOf(25), page1Ages.get(0));
        assertEquals(Long.valueOf(30), page1Ages.get(1));
        assertEquals(Long.valueOf(35), page2Ages.get(0));
        assertEquals(Long.valueOf(40), page2Ages.get(1));
    }

    /**
     * Test: Invalid page numbers (0 and negative) default to page 1
     * Verifies that page=0 and page=-1 are treated as page=1 and return first page results
     */
    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_DATA_STUDIO})
    public void testInvalidPageNumbersDefaultToPageOne() throws Exception {
        DataQueryResponse page1 = controller.query(testEntity.getId(), null, 1, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(2, page1.getRecords().size());
        assertEquals(Long.valueOf(25), page1.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(30), page1.getRecords().get(1).getValues().get("Age"));

        DataQueryResponse page0 = controller.query(testEntity.getId(), null, 0, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(2, page0.getRecords().size());
        assertEquals(Long.valueOf(25), page0.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(30), page0.getRecords().get(1).getValues().get("Age"));

        DataQueryResponse pageNegative = controller.query(testEntity.getId(), null, -1, PageDirection.next.name(), 2, "Age", "asc", null);
        assertEquals(2, pageNegative.getRecords().size());
        assertEquals(Long.valueOf(25), pageNegative.getRecords().get(0).getValues().get("Age"));
        assertEquals(Long.valueOf(30), pageNegative.getRecords().get(1).getValues().get("Age"));

        assertEquals("Page 0 should return same results as page 1",
                     page1.getRecords().get(0).getValues().get("Age"),
                     page0.getRecords().get(0).getValues().get("Age"));
        assertEquals("Page -1 should return same results as page 1",
                     page1.getRecords().get(0).getValues().get("Age"),
                     pageNegative.getRecords().get(0).getValues().get("Age"));
    }

    // Helper methods

    private EntityDefinition createTestEntity() {
        EntityDefinition entity = new EntityDefinition(TEST_ENTITY_NAME, TEST_ENTITY_NAME);
        entity.setDraftStatus(DraftStatus.APPROVED);
        entity.setConnectorId(connectorService.getSyncariConnector().getId());
        entity = entityDefinitionRepo.save(entity);

        // Create Name field (String)
        AttributeDefinition name = new AttributeDefinition()
            .setApiName("Name")
            .setDataType(new StringType())
            .setDisplayName("Name")
            .setEntityId(entity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeRepo.save(name);

        // Create Age field (Integer)
        AttributeDefinition age = new AttributeDefinition()
            .setApiName("Age")
            .setDataType(new IntegerType())
            .setDisplayName("Age")
            .setEntityId(entity.getId());
        age.setDraftStatus(DraftStatus.APPROVED);
        age.setStatus(Status.ACTIVE);
        age = attributeRepo.save(age);

        // Create Salary field (Double)
        AttributeDefinition salary = new AttributeDefinition()
            .setApiName("Salary")
            .setDataType(new DoubleType())
            .setDisplayName("Salary")
            .setEntityId(entity.getId());
        salary.setDraftStatus(DraftStatus.APPROVED);
        salary.setStatus(Status.ACTIVE);
        salary = attributeRepo.save(salary);

        entity.addField(name);
        entity.addField(age);
        entity.addField(salary);

        return entity;
    }

    private EntityDefinition createEmptyEntity() {
        EntityDefinition entity = new EntityDefinition("EmptyEntity", "EmptyEntity");
        entity.setDraftStatus(DraftStatus.APPROVED);
        entity.setConnectorId(connectorService.getSyncariConnector().getId());
        entity = entityDefinitionRepo.save(entity);

        AttributeDefinition name = new AttributeDefinition()
            .setApiName("Name")
            .setDataType(new StringType())
            .setDisplayName("Name")
            .setEntityId(entity.getId());
        name.setDraftStatus(DraftStatus.APPROVED);
        name.setStatus(Status.ACTIVE);
        name = attributeRepo.save(name);

        entity.addField(name);
        return entity;
    }

    private List<EntityData> createTestData() {
        EntityData record1 = new EntityData();
        record1.setName(TEST_ENTITY_NAME);
        record1.addValue("Name", "Alice");
        record1.addValue("Age", 30);
        record1.addValue("Salary", 60000.0);
        record1 = entityRepo.save(testEntity, record1);

        EntityData record2 = new EntityData();
        record2.setName(TEST_ENTITY_NAME);
        record2.addValue("Name", "Bob");
        record2.addValue("Age", 25);
        record2.addValue("Salary", 50000.0);
        record2 = entityRepo.save(testEntity, record2);

        EntityData record3 = new EntityData();
        record3.setName(TEST_ENTITY_NAME);
        record3.addValue("Name", "Charlie");
        record3.addValue("Age", 35);
        record3.addValue("Salary", 70000.0);
        record3 = entityRepo.save(testEntity, record3);

        EntityData record4 = new EntityData();
        record4.setName(TEST_ENTITY_NAME);
        record4.addValue("Name", "David");
        record4.addValue("Age", 40);
        record4.addValue("Salary", 80000.0);
        record4 = entityRepo.save(testEntity, record4);

        EntityData record5 = new EntityData();
        record5.setName(TEST_ENTITY_NAME);
        record5.addValue("Name", "Eve");
        record5.addValue("Age", 45);
        record5.addValue("Salary", 90000.0);
        record5 = entityRepo.save(testEntity, record5);

        return Arrays.asList(record1, record2, record3, record4, record5);
    }
}
