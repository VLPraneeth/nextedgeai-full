package com.syncari.connector;

import com.syncari.connector.data.AttributeSchema;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UpdateFieldResponse {

    private boolean isFieldUpdated;
    private AttributeSchema currentSchema;
    private AttributeSchema newSchema;
}
