package com.syncari.connector.data.iterator.intercom;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Pagination {

    Integer per_page;
    String starting_after;
}
