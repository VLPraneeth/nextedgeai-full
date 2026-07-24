package com.syncari.api.rest.controllers.data;

import lombok.Data;

@Data
public class UpdateFieldMappingDTO {

    private FieldMappingDTO existing;
    private FieldMappingDTO updated;
}
