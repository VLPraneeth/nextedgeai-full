package com.syncari.core.model.misc;

import lombok.Data;

@Data
public class UpdateFieldMappingRequest {

    private FieldMapping existing;
    private FieldMapping updated;
}
