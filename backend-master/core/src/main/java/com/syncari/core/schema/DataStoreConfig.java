package com.syncari.core.schema;

import lombok.Data;

import java.io.Serializable;

@Data
public class DataStoreConfig implements Serializable {
    String oldName;
    String newName;
}