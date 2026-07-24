package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Getter
public class FirstNoMatchRank {

    final int rank;
    final String recordId;
    final Object value;
    final EntityData entityData;

    public boolean hasRank() {
        return rank != Integer.MAX_VALUE;
    }

    public static FirstNoMatchRank rank(int couldBeRank, Object value, String recordId, List rankedValues, EntityData entityData) {
        if (!rankedValues.contains(value)){
            return new FirstNoMatchRank(couldBeRank, recordId, value,entityData);
        }
        //There is match
        return new FirstNoMatchRank(Integer.MAX_VALUE, recordId, value,entityData);
    }

    public static Optional<FirstNoMatchRank> first(List<FirstNoMatchRank> ranks) {
        return ranks.stream().filter(r -> r.hasRank()).min(Comparator.comparingInt(FirstNoMatchRank::getRank));
    }
}
