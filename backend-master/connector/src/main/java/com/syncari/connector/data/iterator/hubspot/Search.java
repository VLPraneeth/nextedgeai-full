package com.syncari.connector.data.iterator.hubspot;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class Search {
    List<String> properties;
    int limit;
    String after;
    List<Map<String, String>> sorts;
    List<FilterGroup> filterGroups = new ArrayList<>();

    public Search addFilterGroup(FilterGroup group) {
        filterGroups.add(group);
        return this;
    }
}
