package com.syncari.core.model.misc;

import com.syncari.connector.Constants;
import com.syncari.core.exceptions.SyncariValidationException;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ConnectorSetting {
    int syncRate;
    long apiQuota;
    boolean bootstrapWithSyncari;
    @Setter(AccessLevel.NONE)
    private Map<String, Object> internalConfig = new HashMap<>();
    private final static List<String> allowedKeys = List.of(Constants.SKIP_CLOCK_SKEW);

    public void addInternalConfig(String key, Object value) {
        if(!allowedKeys.contains(key)) throw new SyncariValidationException("Invalid key "+key);
        internalConfig.put(key, value);
    }
}
