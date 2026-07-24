package com.syncari.core.model.pagination;

import java.util.List;

import lombok.Data;

@Data
public class Page<T> {
    public static final int MAX_PAGE_SIZE=1001;
    PageInfo pageInfo;
    List<T> records;
    
    public Page(PageInfo pageInfo, List<T> records) {
        this.pageInfo = pageInfo;
        this.records = records;
    }
    
    public Page() {}
}
