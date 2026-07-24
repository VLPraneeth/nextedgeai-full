package com.syncari.core.model.insights.provider.ts;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TSExtDatabase {

    private String name;
    private boolean isAutoCreated;
    private List<TSSchema> schemas;
}
