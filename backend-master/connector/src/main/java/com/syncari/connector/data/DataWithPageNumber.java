package com.syncari.connector.data;

import java.util.ArrayList;
import java.util.List;

import com.syncari.connector.EntityData;

import lombok.Data;

@Data
public class DataWithPageNumber {
    final int prevPageNumber;
    final int nextPageNumber;
    final List<EntityData> data;
    final List<String> errors;

    public DataWithPageNumber(int prevPageNumber, int nextPageNumber, List<EntityData> data, List<String> errors) {
        this.prevPageNumber = prevPageNumber;
        this.nextPageNumber = nextPageNumber;
        this.data = data;
        this.errors = errors;
    }

    public static DataWithPageNumber emptyWithOffsets(int prevPageNumber, int nextPageNumber) {
        return new DataWithPageNumber(prevPageNumber, nextPageNumber, new ArrayList<>(), new ArrayList<>());
    }

    public static DataWithPageNumber emptyWithErrors(int prevPageNumber, int nextPageNumber, List<String> errors) {
        return new DataWithPageNumber(prevPageNumber, nextPageNumber, new ArrayList<>(), errors);
    }
}