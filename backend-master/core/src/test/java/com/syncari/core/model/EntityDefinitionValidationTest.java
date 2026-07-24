package com.syncari.core.model;

import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit tests for EntityDefinition validation methods
 */
public class EntityDefinitionValidationTest {

    @Test
    public void testValidateMultipleWatermarks_shouldFailWithTwoWatermarks() {
        // Create entity with two watermark fields
        EntityDefinition entity = SchemaHelper.createEntityDefinition("test_entity")
                .watermark("created_at", new DatetimeType())
                .watermark("updated_at", new DatetimeType())
                .id()
                .getEntityDefinition();

        try {
            entity.validateMultipleWatermarks();
            fail("Expected SyncariValidationException for multiple watermarks");
        } catch (SyncariValidationException e) {
            assertEquals("The entity test_entity has multiple watermark fields defined", e.getMessage());
        }
    }

    @Test
    public void testValidateMultipleWatermarks_shouldPassWithOneWatermark() {
        // Create entity with single watermark field
        EntityDefinition entity = SchemaHelper.createEntityDefinition("test_entity")
                .watermark("updated_at", new DatetimeType())
                .id()
                .getEntityDefinition();

        // Should not throw exception
        entity.validateMultipleWatermarks();
    }

    @Test
    public void testValidateMultipleWatermarks_shouldPassWithNoWatermark() {
        // Create entity with no watermark field
        EntityDefinition entity = SchemaHelper.createEntityDefinition("test_entity")
                .string("name")
                .id()
                .getEntityDefinition();

        // Should not throw exception
        entity.validateMultipleWatermarks();
    }

    @Test
    public void testValidateMultipleWatermarks_shouldFailWithThreeWatermarks() {
        // Create entity with three watermark fields
        EntityDefinition entity = SchemaHelper.createEntityDefinition("test_entity")
                .watermark("created_at", new DatetimeType())
                .watermark("updated_at", new DatetimeType())
                .watermark("synced_at", new DatetimeType())
                .id()
                .getEntityDefinition();

        try {
            entity.validateMultipleWatermarks();
            fail("Expected SyncariValidationException for multiple watermarks");
        } catch (SyncariValidationException e) {
            assertEquals("The entity test_entity has multiple watermark fields defined", e.getMessage());
        }
    }
}
