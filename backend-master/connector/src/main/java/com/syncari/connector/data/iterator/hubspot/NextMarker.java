package com.syncari.connector.data.iterator.hubspot;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NextMarker {
    String after;
}
