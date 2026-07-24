package com.syncari.connector.data.iterator.intercom;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class Query {

    String field;
    String operator;
    Object value;

}
