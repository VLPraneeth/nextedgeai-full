package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Helper class for ranking values based on case-insensitive matching against a list of ranked values.
 * Similar to FirstMatchRank but performs case-insensitive comparison.
 */
@AllArgsConstructor
@Getter
public class FirstMatchRankIgnoreCase {
    final int rank;
    final String recordId;
    final String value;
    final EntityData entityData;

    public boolean hasRank() {
        return rank != Integer.MAX_VALUE;
    }

    /**
     * Ranks a value based on its position in the rankedValues list using case-insensitive comparison.
     * @param value The value to rank
     * @param recordId The record ID
     * @param rankedValues List of values to match against (case-insensitive)
     * @param ed The entity data
     * @return FirstMatchRankIgnoreCase with rank equal to the index where it matched, or Integer.MAX_VALUE if no match
     */
    public static FirstMatchRankIgnoreCase rank(String value, String recordId, List rankedValues, EntityData ed) {
        if (value == null) {
            // No match for null values
            return new FirstMatchRankIgnoreCase(Integer.MAX_VALUE, recordId, value, ed);
        }

        for (int i = 0; i < rankedValues.size(); i++) {
            Object rankedValue = rankedValues.get(i);
            if (rankedValue != null && value.equalsIgnoreCase(rankedValue.toString())) {
                return new FirstMatchRankIgnoreCase(i, recordId, value, ed);
            }
        }
        // No match
        return new FirstMatchRankIgnoreCase(Integer.MAX_VALUE, recordId, value, ed);
    }

    /**
     * Returns the FirstMatchRankIgnoreCase with the lowest rank (first match).
     * @param ranks List of ranks
     * @return Optional containing the first matching rank, or empty if no matches
     */
    public static Optional<FirstMatchRankIgnoreCase> first(List<FirstMatchRankIgnoreCase> ranks) {
        return ranks.stream().filter(r -> r.hasRank()).min(Comparator.comparingInt(FirstMatchRankIgnoreCase::getRank));
    }
}
