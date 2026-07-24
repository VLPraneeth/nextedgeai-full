package com.syncari.connector;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EntityPage {
    protected long offset;
    protected boolean hasMore;
    protected List<EntityData> data = new ArrayList<>();

    public EntityPage addRecord(EntityData record){
        data.add(record);
        return this;
    }
}
