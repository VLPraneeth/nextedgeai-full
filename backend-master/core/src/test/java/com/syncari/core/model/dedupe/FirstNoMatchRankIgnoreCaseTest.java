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
 * Tests for FirstNoMatchRankIgnoreCase class - case-insensitive non-matching functionality
 */
public class FirstNoMatchRankIgnoreCaseTest {

    @Test
    public void testRankWithCaseInsensitiveNoMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "UniqueValue");

        // Test case-insensitive non-matching
        List<String> excludeValues = Arrays.asList("testvalue", "anothervalue", "thirdvalue");

        FirstNoMatchRankIgnoreCase rank = FirstNoMatchRankIgnoreCase.rank(0, "UniqueValue", record.getId(), excludeValues, record);

        assertTrue("Should have a valid rank for non-matching value", rank.hasRank());
        assertEquals("Should have rank 0", 0, rank.getRank());
        assertEquals("Should preserve original value", "UniqueValue", rank.getValue());
    }

    @Test
    public void testRankWithCaseInsensitiveMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "TestValue");

        // Test that matching occurs case-insensitively
        List<String> excludeValues = Arrays.asList("OTHER", "TESTVALUE", "THIRD");

        FirstNoMatchRankIgnoreCase rank = FirstNoMatchRankIgnoreCase.rank(0, "TestValue", record.getId(), excludeValues, record);

        assertFalse("Should NOT have a valid rank when value matches (case-insensitive)", rank.hasRank());
        assertEquals("Should have MAX_VALUE rank for match", Integer.MAX_VALUE, rank.getRank());
        assertEquals("Should preserve original value", "TestValue", rank.getValue());
    }

    @Test
    public void testRankWithUpperCaseMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "value");

        // Test matching with uppercase in exclude list
        List<String> excludeValues = Arrays.asList("OTHER", "VALUE", "THIRD");

        FirstNoMatchRankIgnoreCase rank = FirstNoMatchRankIgnoreCase.rank(1, "value", record.getId(), excludeValues, record);

        assertFalse("Should NOT have a valid rank when matched", rank.hasRank());
        assertEquals("Should have MAX_VALUE rank", Integer.MAX_VALUE, rank.getRank());
    }

    @Test
    public void testRankWithMixedCaseMatch() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "MiXeDcAsE");

        // Test matching with different mixed case
        List<String> excludeValues = Arrays.asList("nomatch", "mixedcase", "another");

        FirstNoMatchRankIgnoreCase rank = FirstNoMatchRankIgnoreCase.rank(0, "MiXeDcAsE", record.getId(), excludeValues, record);

        assertFalse("Should NOT have a valid rank when matched case-insensitively", rank.hasRank());
        assertEquals("Should have MAX_VALUE rank", Integer.MAX_VALUE, rank.getRank());
    }

    @Test
    public void testRankWithNoMatchInList() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "NoMatch");

        List<String> excludeValues = Arrays.asList("value1", "value2", "value3");

        FirstNoMatchRankIgnoreCase rank = FirstNoMatchRankIgnoreCase.rank(5, "NoMatch", record.getId(), excludeValues, record);

        assertTrue("Should have a valid rank for non-matching value", rank.hasRank());
        assertEquals("Should have the provided rank", 5, rank.getRank());
        assertEquals("Should preserve original value", "NoMatch", rank.getValue());
    }

    @Test
    public void testRankWithNullValue() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef);

        List<String> excludeValues = Arrays.asList("value1", "value2", "value3");

        FirstNoMatchRankIgnoreCase rank = FirstNoMatchRankIgnoreCase.rank(2, null, record.getId(), excludeValues, record);

        assertTrue("Should have a valid rank for null value (considered as non-matching)", rank.hasRank());
        assertEquals("Should have the provided rank", 2, rank.getRank());
        assertNull("Should have null value", rank.getValue());
    }

    @Test
    public void testFirstNonMatchWithMultipleRanks() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record1 = createRecord(entityDef).addValue("testField", "Match1");
        EntityData record2 = createRecord(entityDef).addValue("testField", "NoMatch1");
        EntityData record3 = createRecord(entityDef).addValue("testField", "Match2");
        EntityData record4 = createRecord(entityDef).addValue("testField", "NoMatch2");

        List<String> excludeValues = Arrays.asList("MATCH1", "MATCH2");

        FirstNoMatchRankIgnoreCase rank1 = FirstNoMatchRankIgnoreCase.rank(0, "Match1", record1.getId(), excludeValues, record1);
        FirstNoMatchRankIgnoreCase rank2 = FirstNoMatchRankIgnoreCase.rank(1, "NoMatch1", record2.getId(), excludeValues, record2);
        FirstNoMatchRankIgnoreCase rank3 = FirstNoMatchRankIgnoreCase.rank(2, "Match2", record3.getId(), excludeValues, record3);
        FirstNoMatchRankIgnoreCase rank4 = FirstNoMatchRankIgnoreCase.rank(3, "NoMatch2", record4.getId(), excludeValues, record4);

        List<FirstNoMatchRankIgnoreCase> ranks = Arrays.asList(rank1, rank2, rank3, rank4);

        Optional<FirstNoMatchRankIgnoreCase> firstNonMatch = FirstNoMatchRankIgnoreCase.first(ranks);

        assertTrue("Should find a first non-match", firstNonMatch.isPresent());
        assertEquals("Should find 'NoMatch1' as first non-match (rank 1)", "NoMatch1", firstNonMatch.get().getValue());
        assertEquals("Should have rank 1", 1, firstNonMatch.get().getRank());
    }

    @Test
    public void testFirstNonMatchWithAllMatching() {
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record1 = createRecord(entityDef).addValue("testField", "Value1");
        EntityData record2 = createRecord(entityDef).addValue("testField", "Value2");

        List<String> excludeValues = Arrays.asList("VALUE1", "VALUE2");

        FirstNoMatchRankIgnoreCase rank1 = FirstNoMatchRankIgnoreCase.rank(0, "Value1", record1.getId(), excludeValues, record1);
        FirstNoMatchRankIgnoreCase rank2 = FirstNoMatchRankIgnoreCase.rank(1, "Value2", record2.getId(), excludeValues, record2);

        List<FirstNoMatchRankIgnoreCase> ranks = Arrays.asList(rank1, rank2);

        Optional<FirstNoMatchRankIgnoreCase> firstNonMatch = FirstNoMatchRankIgnoreCase.first(ranks);

        assertFalse("Should not find any non-match when all values match", firstNonMatch.isPresent());
    }

    @Test
    public void testCaseSensitiveComparison() {
        // Verify that regular FirstNoMatchRank would match differently than FirstNoMatchRankIgnoreCase
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record = createRecord(entityDef).addValue("testField", "TestValue");

        List<String> excludeValues = Arrays.asList("TESTVALUE", "OTHER");

        // Case-sensitive comparison (FirstNoMatchRank) would NOT match, so it returns a valid rank
        FirstNoMatchRank caseSensitiveRank = FirstNoMatchRank.rank(0, "TestValue", record.getId(), excludeValues, record);
        assertTrue("Case-sensitive should NOT match and return valid rank", caseSensitiveRank.hasRank());
        assertEquals("Should have rank 0", 0, caseSensitiveRank.getRank());

        // Case-insensitive comparison (FirstNoMatchRankIgnoreCase) should match, so no valid rank
        FirstNoMatchRankIgnoreCase caseInsensitiveRank = FirstNoMatchRankIgnoreCase.rank(0, "TestValue", record.getId(), excludeValues, record);
        assertFalse("Case-insensitive should match and return no valid rank", caseInsensitiveRank.hasRank());
        assertEquals("Should have MAX_VALUE rank", Integer.MAX_VALUE, caseInsensitiveRank.getRank());
    }

    @Test
    public void testSortedRanking() {
        // Test that ranks preserve the sort order index
        EntityDefinition entityDef = SchemaHelper.createEntityDefinition("test")
                .id()
                .field("testField", StringType.VALUE)
                .getEntityDefinition();

        EntityData record1 = createRecord(entityDef).addValue("testField", "First");
        EntityData record2 = createRecord(entityDef).addValue("testField", "Second");
        EntityData record3 = createRecord(entityDef).addValue("testField", "Third");

        List<String> excludeValues = Arrays.asList("SECOND"); // Only exclude Second

        // Simulate sorted order with indices
        FirstNoMatchRankIgnoreCase rank1 = FirstNoMatchRankIgnoreCase.rank(0, "First", record1.getId(), excludeValues, record1);
        FirstNoMatchRankIgnoreCase rank2 = FirstNoMatchRankIgnoreCase.rank(1, "Second", record2.getId(), excludeValues, record2);
        FirstNoMatchRankIgnoreCase rank3 = FirstNoMatchRankIgnoreCase.rank(2, "Third", record3.getId(), excludeValues, record3);

        assertTrue("First should not match", rank1.hasRank());
        assertEquals("First should have rank 0", 0, rank1.getRank());

        assertFalse("Second should match and be excluded", rank2.hasRank());

        assertTrue("Third should not match", rank3.hasRank());
        assertEquals("Third should have rank 2", 2, rank3.getRank());

        List<FirstNoMatchRankIgnoreCase> ranks = Arrays.asList(rank1, rank2, rank3);
        Optional<FirstNoMatchRankIgnoreCase> firstNonMatch = FirstNoMatchRankIgnoreCase.first(ranks);

        assertTrue("Should find first non-match", firstNonMatch.isPresent());
        assertEquals("Should be 'First' with rank 0", "First", firstNonMatch.get().getValue());
        assertEquals("Should have rank 0", 0, firstNonMatch.get().getRank());
    }
}
