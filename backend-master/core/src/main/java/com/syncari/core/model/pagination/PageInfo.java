package com.syncari.core.model.pagination;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PageInfo {
    String start;
    String end;
    boolean hasMore;
    boolean hasPrevious;
    List<Sort> sorting = new ArrayList<>();
    int pageNumber = 0;
    int maxPageNumber = 0;
    long filteredCount = 0;
    long totalCount = 0;
    String message;

    public PageInfo() {}

    public PageInfo(String start, String end, boolean hasMore) {
        this.start = start;
        this.end = end;
        this.hasMore = hasMore;
    }

    public PageInfo(int pageNumber, int maxPageNumber) {
        this.pageNumber = pageNumber;
        this.maxPageNumber = maxPageNumber;
        this.hasMore = pageNumber < maxPageNumber;
    }

    public PageInfo addSort(String column, boolean asc) {
        sorting.add(new Sort(column, asc));
        return this;
    }

    @Override
    public String toString(){
        return "PageInfo{" + "start='" + start + '\'' + ", end='" + end + '\'' + ", hasMore='" + hasMore + '\''
                + ", pageNumber='" + pageNumber + '\'' + ", maxPageNumber='" + maxPageNumber + '\'' + ", totalCount="
                + totalCount + '}';
    }
}
