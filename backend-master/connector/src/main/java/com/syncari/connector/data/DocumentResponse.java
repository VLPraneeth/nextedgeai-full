package com.syncari.connector.data;

import com.syncari.connector.EntityData;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
@ToString
public class DocumentResponse {
    String entityName;
    private final InputStream contents;
    private final EntityData fileMetadata;
    private Map<String, InputStream> contentMap = new HashMap<>();

    public DocumentResponse(InputStream contents, EntityData fileMetadata) {
        this.contents = contents;
        this.fileMetadata = fileMetadata;
        this.entityName = fileMetadata.getName(); 
    }
}
