package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.utils.SchemaHelper;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.syncari.core.utils.RecordHelper.createRecord;
import static org.junit.Assert.*;

/**
 * Tests for FirstMatchRankIgnoreCase class - case-insensitive matching functionality
 */
public class FirstMatchRankIgnoreCaseTest {

    @Test
    public void testRankWithCaseInsensitiveMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "TestValue");

        // Test case-insensitive matching
        List<String> rankedValues = Arrays.asList("testvalue", "anothervalue", "thirdvalue");

        FirstMatchRankIgnoreCase rank = FirstMatchRankIgnoreCase.rank("TestValue", record.getId(), rankedValues, record);

        assertTrue("Should have a valid rank", rank.hasRank());
        assertEquals("Should match at index 0", 0, rank.getRank());
        assertEquals("Should preserve original value", "TestValue", rank.getValue());
    }

    @Test
    public void testRankWithUpperCaseMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "value");

        // Test matching with uppercase in list
        List<String> rankedValues = Arrays.asList("OTHER", "VALUE", "THIRD");

        FirstMatchRankIgnoreCase rank = FirstMatchRankIgnoreCase.rank("value", record.getId(), rankedValues, record);

        assertTrue("Should have a valid rank", rank.hasRank());
        assertEquals("Should match at index 1", 1, rank.getRank());
        assertEquals("Should preserve original value", "value", rank.getValue());
    }

    @Test
    public void testRankWithMixedCaseMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "MiXeDcAsE");

        // Test matching with different mixed case
        List<String> rankedValues = Arrays.asList("nomatch", "mixedcase", "another");

        FirstMatchRankIgnoreCase rank = FirstMatchRankIgnoreCase.rank("MiXeDcAsE", record.getId(), rankedValues, record);

        assertTrue("Should have a valid rank", rank.hasRank());
        assertEquals("Should match at index 1", 1, rank.getRank());
        assertEquals("Should preserve original value", "MiXeDcAsE", rank.getValue());
    }

    @Test
    public void testRankWithNoMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "NoMatch");

        List<String> rankedValues = Arrays.asList("value1", "value2", "value3");

        FirstMatchRankIgnoreCase rank = FirstMatchRankIgnoreCase.rank("NoMatch", record.getId(), rankedValues, record);

        assertFalse("Should not have a valid rank", rank.hasRank());
        assertEquals("Should have MAX_VALUE rank for no match", Integer.MAX_VALUE, rank.getRank());
        assertEquals("Should preserve original value", "NoMatch", rank.getValue());
    }

    @Test
    public void testRankWithNullValue() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef);

        List<String> rankedValues = Arrays.asList("value1", "value2", "value3");

        FirstMatchRankIgnoreCase rank = FirstMatchRankIgnoreCase.rank(null, record.getId(), rankedValues, record);

        assertFalse("Should not have a valid rank for null value", rank.hasRank());
        assertEquals("Should have MAX_VALUE rank for null", Integer.MAX_VALUE, rank.getRank());
        assertNull("Should have null value", rank.getValue());
    }

    @Test
    public void testFirstMatchWithMultipleRanks() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record1 = createRecord(entityDef).addValue("testField", "First");
        EntityData record2 = createRecord(entityDef).addValue("testField", "Second");
        EntityData record3 = createRecord(entityDef).addValue("testField", "Third");
        EntityData record4 = createRecord(entityDef).addValue("testField", "NoMatch");

        List<String> rankedValues = Arrays.asList("THIRD", "SECOND", "FIRST");

        FirstMatchRankIgnoreCase rank1 = FirstMatchRankIgnoreCase.rank("First", record1.getId(), rankedValues, record1);
        FirstMatchRankIgnoreCase rank2 = FirstMatchRankIgnoreCase.rank("Second", record2.getId(), rankedValues, record2);
        FirstMatchRankIgnoreCase rank3 = FirstMatchRankIgnoreCase.rank("Third", record3.getId(), rankedValues, record3);
        FirstMatchRankIgnoreCase rank4 = FirstMatchRankIgnoreCase.rank("NoMatch", record4.getId(), rankedValues, record4);

        List<FirstMatchRankIgnoreCase> ranks = Arrays.asList(rank1, rank2, rank3, rank4);

        Optional<FirstMatchRankIgnoreCase> firstMatch = FirstMatchRankIgnoreCase.first(ranks);

        assertTrue("Should find a first match", firstMatch.isPresent());
        assertEquals("Should find 'Third' as first match (index 0)", "Third", firstMatch.get().getValue());
        assertEquals("Should have rank 0", 0, firstMatch.get().getRank());
    }

    @Test
    public void testFirstMatchWithNoValidRanks() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record1 = createRecord(entityDef).addValue("testField", "Value1");
        EntityData record2 = createRecord(entityDef).addValue("testField", "Value2");

        List<String> rankedValues = Arrays.asList("NoMatch1", "NoMatch2");

        FirstMatchRankIgnoreCase rank1 = FirstMatchRankIgnoreCase.rank("Value1", record1.getId(), rankedValues, record1);
        FirstMatchRankIgnoreCase rank2 = FirstMatchRankIgnoreCase.rank("Value2", record2.getId(), rankedValues, record2);

        List<FirstMatchRankIgnoreCase> ranks = Arrays.asList(rank1, rank2);

        Optional<FirstMatchRankIgnoreCase> firstMatch = FirstMatchRankIgnoreCase.first(ranks);

        assertFalse("Should not find any match", firstMatch.isPresent());
    }

    @Test
    public void testCaseSensitiveComparison() {
        // Verify that regular FirstMatchRank would NOT match, but FirstMatchRankIgnoreCase does
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "TestValue");

        List<String> rankedValues = Arrays.asList("TESTVALUE", "OTHER");

        // Case-sensitive comparison (FirstMatchRank) would not match
        FirstMatchRank caseSensitiveRank = FirstMatchRank.rank("TestValue", record.getId(), rankedValues, record);
        assertFalse("Case-sensitive should NOT match", caseSensitiveRank.hasRank());

        // Case-insensitive comparison (FirstMatchRankIgnoreCase) should match
        FirstMatchRankIgnoreCase caseInsensitiveRank = FirstMatchRankIgnoreCase.rank("TestValue", record.getId(), rankedValues, record);
        assertTrue("Case-insensitive should match", caseInsensitiveRank.hasRank());
        assertEquals("Should match at index 0", 0, caseInsensitiveRank.getRank());
    }
}
