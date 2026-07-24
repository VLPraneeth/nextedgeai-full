package com.syncari.connector;

import lombok.Data;

@Data
public class JiraEntityPage extends EntityPage {
    String nextPageToken;
    boolean last;

    public String getNextPage() {
        return nextPageToken;
    }

    public long getLatestWatermark() {
        return data.stream().mapToLong(d -> d.getLastModified()).max().orElse(0L);
    }
}