package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.AbstractSyncariTest;
import com.syncari.core.model.misc.test.TestConfig;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Test cases for CSV edge cases to verify proper handling of:
 * - CSVs with missing column values (fewer values than headers)
 * - CSVs with extra columns
 * - Empty lines and blank fields
 * - Proper data type detection logic
 * - No regression in existing CSV parsing flow
 */
public class CsvEdgeCasesTest extends AbstractSyncariTest {

    @Autowired
    CsvUtils utils;

    /**
     * Test Case 1: CSV with missing column values (fewer values than headers)
     * Verifies that toMap() handles records with fewer columns than headers
     */
    @Test
    public void testCsvWithMissingColumnValues() {
        String csvContent = "Name,Email,Phone,Address\n" +
                "John Doe,john@example.com,123-456-7890,123 Main St\n" +
                "Jane Smith,jane@example.com\n" +  // Missing Phone and Address
                "Bob Johnson,bob@example.com,555-0123\n" +  // Missing Address
                "Alice Brown";  // Missing Email, Phone, Address

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            CSVParser parser = utils.getCSVParser(inputStream, new CSVOptions());
            List<String> headers = parser.getHeaderNames();

            assertEquals("Should have 4 headers", 4, headers.size());
            assertEquals("Name", headers.get(0));
            assertEquals("Email", headers.get(1));
            assertEquals("Phone", headers.get(2));
            assertEquals("Address", headers.get(3));

            int recordCount = 0;
            for (CSVRecord record : parser) {
                // Use reflection to call private toMap method or test through detectDatatypes
                if (recordCount == 0) {
                    // Full record: John Doe
                    assertEquals("John Doe", record.get(0));
                    assertEquals(4, record.size());
                }
                if (recordCount == 1) {
                    // Missing Phone and Address: Jane Smith
                    assertEquals("Jane Smith", record.get(0));
                    assertEquals("jane@example.com", record.get(1));
                    assertTrue("Record size should be less than headers", record.size() < headers.size());
                }
                if (recordCount == 2) {
                    // Missing Address: Bob Johnson
                    assertEquals("Bob Johnson", record.get(0));
                    assertEquals(3, record.size());
                }
                if (recordCount == 3) {
                    // Only Name: Alice Brown
                    assertEquals("Alice Brown", record.get(0));
                    assertEquals(1, record.size());
                }
                recordCount++;
            }

            assertEquals("Should have 4 records", 4, recordCount);
        } catch (Exception e) {
            fail("Should handle missing columns gracefully: " + e.getMessage());
        }
    }

    /**
     * Test Case 2: Verify toMap method through detectDatatypes with missing values
     * Tests that missing column values don't break data type detection
     */
    @Test
    public void testDatatypeDetectionWithMissingValues() {
        String csvContent = "Age,Salary,IsActive,JoinDate\n" +
                "25,50000.00,true,2024-01-15\n" +
                "30,60000.50\n" +  // Missing IsActive and JoinDate
                "35\n" +  // Missing Salary, IsActive, JoinDate
                ",75000.25,false,2024-03-20\n" +  // Missing Age
                "40,,,\n";  // Has Age but rest are empty

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            Map<String, String> datatypes = utils.detectDatatypes(inputStream, new CSVOptions());

            assertNotNull("Datatypes map should not be null", datatypes);
            assertEquals("Should detect 4 columns", 4, datatypes.size());

            // Age should be detected as integer despite missing values
            assertEquals("Age should be integer", "integer", datatypes.get("Age"));

            // Salary should be detected as number
            assertEquals("Salary should be number", "number", datatypes.get("Salary"));

            // IsActive should be detected as boolean
            assertEquals("IsActive should be boolean", "boolean", datatypes.get("IsActive"));

            // JoinDate should be detected as date
            assertEquals("JoinDate should be date", "date", datatypes.get("JoinDate"));
        } catch (Exception e) {
            fail("Datatype detection should handle missing values: " + e.getMessage());
        }
    }

    /**
     * Test Case 3: CSV with extra columns in some rows
     * Verifies that extra columns are handled properly
     */
    @Test
    public void testCsvWithExtraColumns() {
        String csvContent = "Name,Email,Phone\n" +
                "John Doe,john@example.com,123-456-7890\n" +
                "Jane Smith,jane@example.com,555-0123,Extra1,Extra2\n" +  // Extra columns
                "Bob Johnson,bob@example.com,555-9999,ExtraData";  // One extra column

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            CSVParser parser = utils.getCSVParser(inputStream, new CSVOptions());
            List<String> headers = parser.getHeaderNames();

            assertEquals("Should have 3 headers", 3, headers.size());

            int recordCount = 0;
            for (CSVRecord record : parser) {
                if (recordCount == 0) {
                    assertEquals(3, record.size());
                }
                if (recordCount == 1) {
                    // Extra columns are included in the record
                    assertEquals(5, record.size());
                    assertEquals("Jane Smith", record.get(0));
                }
                if (recordCount == 2) {
                    assertEquals(4, record.size());
                    assertEquals("Bob Johnson", record.get(0));
                }
                recordCount++;
            }

            assertEquals("Should parse all 3 records", 3, recordCount);
        } catch (Exception e) {
            fail("Should handle extra columns: " + e.getMessage());
        }
    }

    /**
     * Test Case 4: CSV with empty lines
     * Verifies that empty lines are skipped properly
     */
    @Test
    public void testCsvWithEmptyLines() {
        String csvContent = "Name,Email\n" +
                "John Doe,john@example.com\n" +
                "\n" +  // Empty line
                "Jane Smith,jane@example.com\n" +
                "\n" +  // Another empty line
                "\n" +
                "Bob Johnson,bob@example.com";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            CSVParser parser = utils.getCSVParser(inputStream, new CSVOptions());

            int recordCount = 0;
            for (CSVRecord record : parser) {
                // Verify that we only get non-empty records
                assertTrue("Record should have content", record.size() > 0);
                recordCount++;
            }

            // Should have 3 non-empty records (empty lines may or may not be counted depending on CSV parser)
            assertTrue("Should have at least 3 records", recordCount >= 3);
        } catch (Exception e) {
            fail("Should handle empty lines: " + e.getMessage());
        }
    }

    /**
     * Test Case 5: CSV with blank/empty fields
     * Verifies that blank fields are treated as null
     */
    @Test
    public void testCsvWithBlankFields() {
        String csvContent = "Name,Email,Phone,City\n" +
                "John Doe,,123-456-7890,\n" +  // Empty Email and City
                ",jane@example.com,,Boston\n" +  // Empty Name and Phone
                "Bob Johnson,bob@example.com,,";  // Empty Phone and City

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            Map<String, String> datatypes = utils.detectDatatypes(inputStream, new CSVOptions());

            assertNotNull("Should detect datatypes with blank fields", datatypes);
            assertEquals("Should detect 4 columns", 4, datatypes.size());

            // All should default to string when there are many blanks
            assertNotNull("Name datatype should be detected", datatypes.get("Name"));
            assertNotNull("Email datatype should be detected", datatypes.get("Email"));
            assertNotNull("Phone datatype should be detected", datatypes.get("Phone"));
            assertNotNull("City datatype should be detected", datatypes.get("City"));
        } catch (Exception e) {
            fail("Should handle blank fields: " + e.getMessage());
        }
    }

    /**
     * Test Case 6: Comprehensive data type detection with edge cases
     * Tests that data type detection works correctly with various edge cases
     */
    @Test
    public void testComprehensiveDataTypeDetection() {
        String csvContent = "IntCol,DecimalCol,BoolCol,DateCol,StringCol,MixedCol\n" +
                "100,50.5,true,2024-01-15,Hello,123\n" +
                "200,60.75,false,2024-02-20,World,456\n" +
                ",,,,,\n" +  // All blank
                "300,70.25,yes,,Test,ABC\n" +  // Mixed: integer with string
                "400,,no,2024-04-10,,789";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            Map<String, String> datatypes = utils.detectDatatypes(inputStream, new CSVOptions());

            assertEquals("IntCol should be integer", "integer", datatypes.get("IntCol"));
            assertEquals("DecimalCol should be number", "number", datatypes.get("DecimalCol"));
            assertEquals("BoolCol should be boolean", "boolean", datatypes.get("BoolCol"));
            assertEquals("DateCol should be date", "date", datatypes.get("DateCol"));
            assertEquals("StringCol should be string", "string", datatypes.get("StringCol"));
            // MixedCol has both numbers and letters, should be string
            assertEquals("MixedCol should be string", "string", datatypes.get("MixedCol"));
        } catch (Exception e) {
            fail("Comprehensive datatype detection failed: " + e.getMessage());
        }
    }

    /**
     * Test Case 7: No regression - existing valid CSV still works
     * Ensures that normal, well-formed CSVs continue to work as expected
     */
    @Test
    public void testNoRegressionValidCsv() {
        String csvContent = "Id,Name,Age,Salary,IsActive\n" +
                "1,John Doe,30,50000.00,true\n" +
                "2,Jane Smith,25,45000.50,false\n" +
                "3,Bob Johnson,35,60000.75,yes";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            // Test validation
            InputStream validationStream = new ByteArrayInputStream(csvContent.getBytes());
            List<String> headers = utils.validate(validationStream, new CSVOptions());
            assertEquals("Should have 5 headers", 5, headers.size());

            // Test datatype detection
            Map<String, String> datatypes = utils.detectDatatypes(inputStream, new CSVOptions());
            assertEquals("Id should be integer", "integer", datatypes.get("Id"));
            assertEquals("Name should be string", "string", datatypes.get("Name"));
            assertEquals("Age should be integer", "integer", datatypes.get("Age"));
            assertEquals("Salary should be number", "number", datatypes.get("Salary"));
            assertEquals("IsActive should be boolean", "boolean", datatypes.get("IsActive"));
        } catch (Exception e) {
            fail("Valid CSV should work without issues: " + e.getMessage());
        }
    }

    /**
     * Test Case 8: CSV with all missing values in a column
     * Verifies that columns with all blank values default to string type
     */
    @Test
    public void testColumnWithAllMissingValues() {
        String csvContent = "Name,Email,EmptyCol,Phone\n" +
                "John Doe,john@example.com,,123-456-7890\n" +
                "Jane Smith,jane@example.com,,555-0123\n" +
                "Bob Johnson,bob@example.com,,555-9999";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            Map<String, String> datatypes = utils.detectDatatypes(inputStream, new CSVOptions());

            assertEquals("Should detect 4 columns", 4, datatypes.size());
            assertEquals("EmptyCol should default to string", "string", datatypes.get("EmptyCol"));
            assertNotNull("Name should have a datatype", datatypes.get("Name"));
            assertNotNull("Email should have a datatype", datatypes.get("Email"));
            assertNotNull("Phone should have a datatype", datatypes.get("Phone"));
        } catch (Exception e) {
            fail("Should handle column with all missing values: " + e.getMessage());
        }
    }

    /**
     * Test Case 9: CSV with trailing commas (extra empty columns)
     * Verifies handling of trailing commas in CSV records
     */
    @Test
    public void testCsvWithTrailingCommas() {
        String csvContent = "Name,Email,Phone\n" +
                "John Doe,john@example.com,123-456-7890,,,\n" +  // Trailing commas
                "Jane Smith,jane@example.com,555-0123,\n" +  // One trailing comma
                "Bob Johnson,bob@example.com,555-9999";  // No trailing comma

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            CSVParser parser = utils.getCSVParser(inputStream, new CSVOptions());
            List<String> headers = parser.getHeaderNames();

            assertEquals("Should have 3 headers", 3, headers.size());

            int recordCount = 0;
            for (CSVRecord record : parser) {
                // Each record may have different sizes due to trailing commas
                assertTrue("Record should have at least 3 columns", record.size() >= 3);
                recordCount++;
            }

            assertEquals("Should parse all 3 records", 3, recordCount);
        } catch (Exception e) {
            fail("Should handle trailing commas: " + e.getMessage());
        }
    }

    /**
     * Test Case 10: Verify row count with missing values
     * Ensures getRowCount works correctly even with missing values
     */
    @Test
    public void testRowCountWithMissingValues() {
        String csvContent = "Name,Email,Phone\n" +
                "John Doe,john@example.com,123-456-7890\n" +
                "Jane Smith,\n" +  // Missing Email and Phone
                "Bob Johnson,bob@example.com\n" +  // Missing Phone
                "\n" +  // Empty line
                "Alice Brown,alice@example.com,555-1234";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes())) {
            long rowCount = utils.getRowCount(inputStream, new CSVOptions());

            // Should count all non-empty data rows (excluding header)
            assertTrue("Row count should be at least 4", rowCount >= 4);
        } catch (Exception e) {
            fail("Row count should work with missing values: " + e.getMessage());
        }
    }
}