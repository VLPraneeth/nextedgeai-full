package com.syncari.connector.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class DatastoreFieldMetadata {
    private String aliasName;
    private String fieldExpression;
    private String displayFormat;
    private String apiName;
    private String dataType;
    private String tableName;
}
