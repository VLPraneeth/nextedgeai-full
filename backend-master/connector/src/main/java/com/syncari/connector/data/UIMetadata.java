package com.syncari.connector.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UIMetadata {
    String displayName;
    String iconPath;
    String helpUrl;
    String backgroundColor;

    public UIMetadata() {
    }
}


