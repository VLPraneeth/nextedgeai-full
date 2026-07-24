package com.syncari.connector.data.iterator.intercom;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class Search {
    Query query;
    Pagination pagination;
    Sort sort;

}
