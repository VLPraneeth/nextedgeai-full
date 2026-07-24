package com.syncari.connector;

import lombok.Data;

@Data
public class MarketoEntityPage extends EntityPage {
    String nextPage;

    public long getLatestWatermark(){
        return data.stream().mapToLong(d -> d.getLastModified()).max().orElse(0l);
    }
}
