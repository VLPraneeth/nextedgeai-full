package com.syncari.core.model.misc;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class ZoomPreference {
    private Map<String, Object> zoomAt = new HashMap<>();
}
