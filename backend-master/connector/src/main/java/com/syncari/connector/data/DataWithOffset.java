package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.EntityData;

import lombok.Data;

@Data
public class DataWithOffset {
    final long prevOffset;
    final long nextOffset;
    final List<EntityData> data;
    final List<String> errors;

    public DataWithOffset(long prevOffset, long nextOffset, List<EntityData> data, List<String> errors) {
        this.prevOffset = prevOffset;
        this.nextOffset = nextOffset;
        this.data = data;
        this.errors = errors;
    }

    public static DataWithOffset emptyWithOffsets(long prevOffset, long nextOffset) {
        return new DataWithOffset(prevOffset, nextOffset, new ArrayList<>(), new ArrayList<>());
    }

    public static DataWithOffset emptyWithErrors(long prevOffset, long nextOffset, List<String> errors) {
        return new DataWithOffset(prevOffset, nextOffset, new ArrayList<>(), errors);
    }
}