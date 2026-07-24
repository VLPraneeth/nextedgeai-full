package com.syncari.api.rest.controllers.data.studio;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class DataMetaResponse {
    Map<String, Long> countMap = new HashMap<String, Long>();
    Map<String, Long> deletedMap = new HashMap<String, Long>();
    Map<String, Long> totals = new HashMap<String, Long>();
}
