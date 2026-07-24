package com.syncari.connector.data;

import java.util.List;

import com.syncari.connector.EntityData;

import lombok.Data;

@Data
public class DataWithCursor {
    final String nextPageURL;
    final String prevPageURL;
    final List<EntityData> data;

    public DataWithCursor(String prevPageURL, String nextPageURL, List<EntityData> data) {
        this.prevPageURL = prevPageURL;
        this.nextPageURL = nextPageURL;
        this.data = data;
    }
}