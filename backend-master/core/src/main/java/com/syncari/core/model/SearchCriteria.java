package com.syncari.core.model;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain=true)
@ToString
public class SearchCriteria {
    private Map<String,Object> searchFieldNameValues=new HashMap<>();
    private Map<String,Object> metaFilters=new HashMap<>();
    private boolean matchAll = true;
    private boolean normalizeBeforeMatching = false;
    private boolean caseSensitive = false;
    private boolean ignoreDelimiters = false;

    public SearchCriteria and(String fieldName, Object value){
        searchFieldNameValues.put(fieldName,value);
        return this;
    }
    public SearchCriteria addMetaFilter(String fieldName, Object value){
        metaFilters.put(fieldName,value);
        return this;
    }

    public static SearchCriteria with(String field, Object value){
        return new SearchCriteria().and(field,value);
    }
}
