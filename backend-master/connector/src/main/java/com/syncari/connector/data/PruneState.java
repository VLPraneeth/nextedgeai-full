package com.syncari.connector.data;

import com.syncari.connector.EntityData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@ToString
@AllArgsConstructor
@Accessors(chain = true)
public class PruneState {
    private EntityData lastRecord;
    private long offset;
    private boolean pruned;
    private long timestamp;
}
