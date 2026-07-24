package com.syncari.api.rest.controllers.data.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LayoutDTO {

    int minH;
    int maxH;
    int w;
    int h;
    int x;
    int y;
    boolean resizable;
}
