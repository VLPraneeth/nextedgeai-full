package com.syncari.core.model.insights.dataset;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatasetPageInfo {

    String start;
    String end;
    boolean hasMore;
    boolean hasPrevious;
    long totalCount = 0;

    @Override
    public String toString(){
        return "PageInfo{" + "start='" + start + '\'' + ", end='" + end + '\'' + ", hasMore='" + hasMore + '\''
                + ", hasPrevious='" + hasPrevious + '\'' + '}';
    }
}
