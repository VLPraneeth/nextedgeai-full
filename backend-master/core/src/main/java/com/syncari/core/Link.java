package com.syncari.core;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors( chain = true)
public class Link {
    String displayText;
    Route route;
}
