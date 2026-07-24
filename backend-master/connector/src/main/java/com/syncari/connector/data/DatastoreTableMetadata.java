package com.syncari.connector.data;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class DatastoreTableMetadata {
    private String tableName;
    private String alias;
    private String schemaName;
}
