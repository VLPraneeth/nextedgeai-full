package com.syncari.core.model.misc;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WidgetLayout {
    String id;
    int x;
    int y;
    int h;
    int w;
    Integer minH;
    Integer maxH;
    boolean resizable;
}
