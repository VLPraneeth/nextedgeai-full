package com.syncari.core.model.insights;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DashboardLayout {

    int minH;
    int maxH;
    int width;
    int height;
    int x;
    int y;
    boolean resizable;

    @Override
    public String toString(){
        return String.format( " minH %s maxH %s width %s height %s x %s y %s resizable %s", minH, maxH,width,height, x, y, resizable);
    }
}
