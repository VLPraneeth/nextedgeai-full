package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Helper class for ranking values based on case-insensitive non-matching against a list of values.
 * Similar to FirstNoMatchRank but performs case-insensitive comparison.
 * Returns a rank if the value does NOT match any value in the list (case-insensitive).
 */
@AllArgsConstructor
@Getter
public class FirstNoMatchRankIgnoreCase {

    final int rank;
    final String recordId;
    final String value;
    final EntityData entityData;

    public boolean hasRank() {
        return rank != Integer.MAX_VALUE;
    }

    /**
     * Ranks a value if it does NOT match any value in the rankedValues list (case-insensitive comparison).
     * @param couldBeRank The potential rank (typically the index in sorted order)
     * @param value The value to check
     * @param recordId The record ID
     * @param rankedValues List of values to check against (case-insensitive)
     * @param entityData The entity data
     * @return FirstNoMatchRankIgnoreCase with the rank if no match found, or Integer.MAX_VALUE if there's a match
     */
    public static FirstNoMatchRankIgnoreCase rank(int couldBeRank, String value, String recordId, List<String> rankedValues, EntityData entityData) {
        if (value == null) {
            // Consider null as not matching anything
            return new FirstNoMatchRankIgnoreCase(couldBeRank, recordId, value, entityData);
        }

        // Check if value matches any in rankedValues (case-insensitive)
        for (Object rankedValue : rankedValues) {
            if (rankedValue != null && value.equalsIgnoreCase(rankedValue.toString())) {
                // There is a match, return MAX_VALUE (no rank)
                return new FirstNoMatchRankIgnoreCase(Integer.MAX_VALUE, recordId, value, entityData);
            }
        }

        // No match found, return the rank
        return new FirstNoMatchRankIgnoreCase(couldBeRank, recordId, value, entityData);
    }

    /**
     * Returns the FirstNoMatchRankIgnoreCase with the lowest rank (first non-match).
     * @param ranks List of ranks
     * @return Optional containing the first non-matching rank, or empty if all match
     */
    public static Optional<FirstNoMatchRankIgnoreCase> first(List<FirstNoMatchRankIgnoreCase> ranks) {
        return ranks.stream().filter(r -> r.hasRank()).min(Comparator.comparingInt(FirstNoMatchRankIgnoreCase::getRank));
    }
}
