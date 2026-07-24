package com.syncari.connector.data.iterator.hubspot;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class FilterGroup {
    List<Filter> filters = new ArrayList<>();

    public FilterGroup addFilter(Filter group) {
        filters.add(group);
        return this;
    }
}
