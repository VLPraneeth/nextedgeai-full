package com.syncari.core.model;

import com.syncari.connector.EntityData;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReferencedRecords {
    private Reference reference;
    private List<EntityData> referencedRecords;

}
