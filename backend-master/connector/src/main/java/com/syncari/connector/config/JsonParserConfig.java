package com.syncari.connector.config;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class JsonParserConfig {
    String resultsArrayPath;
    String fieldsPath;
    String associationsPath;
    String idPath;
    String idFieldName;
    boolean isFieldKey;
    String valuePath;
    String offsetPath;
    String hasMorePath;

    public JsonParserConfig(String resultsArrayPath, String fieldsPath, String idPath, String idFieldName,
            boolean isFieldKey, String valuePath,String associationsPath) {
        this(resultsArrayPath, fieldsPath, idPath, idFieldName, isFieldKey, valuePath);
        this.associationsPath = associationsPath;
    }
    
    public JsonParserConfig(String resultsArrayPath, String fieldsPath, String idPath, String idFieldName,
            boolean isFieldKey, String valuePath) {
        super();
        this.resultsArrayPath = resultsArrayPath;
        this.fieldsPath = fieldsPath;
        this.idPath = idPath;
        this.idFieldName = idFieldName;
        this.isFieldKey = isFieldKey;
        this.valuePath = valuePath;
    }
}
