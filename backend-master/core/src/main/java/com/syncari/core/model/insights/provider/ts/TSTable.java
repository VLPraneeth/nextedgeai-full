package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TSTable {

    private String name;
    private String type="TABLE";
    private String description;
    private boolean selected=true;
    private boolean linked=true;
    private List<TSColumn> columns;
}
