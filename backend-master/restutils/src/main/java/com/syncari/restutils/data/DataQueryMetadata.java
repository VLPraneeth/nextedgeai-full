package com.syncari.restutils.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.syncari.utils.KeyValue;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataQueryMetadata {
    Set<String> selectedColumns;
    Map<String, KeyValue> fields = new HashMap<>();
}
