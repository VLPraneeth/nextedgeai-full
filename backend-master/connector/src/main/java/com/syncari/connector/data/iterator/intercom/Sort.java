package com.syncari.connector.data.iterator.intercom;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Sort {
    String field;
    String order;
}
