package com.syncari.viper.model;

import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class RealtimeSyncRequest {
    List<EntityData> entityData;
    EntityDefinition sourceEntity;
}
