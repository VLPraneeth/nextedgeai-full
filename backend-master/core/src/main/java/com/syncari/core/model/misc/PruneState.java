package com.syncari.core.model.misc;

import com.syncari.connector.EntityData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class PruneState {
    private EntityData lastRecordNotPruned;
    private long offset;
    private boolean pruned;
    private long timestamp;
}