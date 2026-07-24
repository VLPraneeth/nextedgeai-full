package com.syncari.core.model.dedupe;

import com.syncari.connector.EntityData;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
@Getter
public class FirstMatchRank {
    final int rank;
    final String recordId;
    final Object value;
    final EntityData entityData;

    public boolean hasRank() {
        return rank != Integer.MAX_VALUE;
    }

    public static FirstMatchRank rank(Object value, String recordId, List rankedValues, EntityData ed) {
        for (int i = 0; i < rankedValues.size(); i++) {
            if (Objects.equals(value,rankedValues.get(i))) {
                return new FirstMatchRank(i, recordId, value, ed);
            }
        }
        //No match
        return new FirstMatchRank(Integer.MAX_VALUE, recordId, value, ed);
    }

    public static Optional<FirstMatchRank> first(List<FirstMatchRank> ranks) {
        return ranks.stream().filter(r -> r.hasRank()).min(Comparator.comparingInt(FirstMatchRank::getRank));
    }
}
