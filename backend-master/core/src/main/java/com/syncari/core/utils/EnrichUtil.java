package com.syncari.core.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class EnrichUtil {

    public static Object findInResponseBody(String fieldPath, Map map) {
        if (map == null) return null;
        String[] fieldParts = fieldPath.split("\\.");
        var currentMap = map;
        try {
            for (int i = 0; i < fieldParts.length; i++) {
                if (currentMap.get(fieldParts[i]) == null) {
                    return null;
                }else if (Map.class.isAssignableFrom(currentMap.get(fieldParts[i]).getClass())) {
                    currentMap = (Map) currentMap.get(fieldParts[i]);
                } else if (List.class.isAssignableFrom(currentMap.get(fieldParts[i]).getClass())) {
                    // if List, retrieve 1st value from it
                    var values = (List) currentMap.get(fieldParts[i]);
                    if (values.isEmpty()) return null;
                    if (!(values.get(0) instanceof Map))  return values.get(0);
                    currentMap = (Map) values.get(0);
                } else {
                    return currentMap.get(fieldParts[i]);
                }
            }
        } catch (Exception e){
            log.error("Error in retrieving {} from response {}", fieldPath, map.toString());
        }
        return null;
    }

    // Todo: Support List of Lists is not there
    public static Object findMultiValueInResponseBody(String fieldPath, Map map) {
        if (map == null) return null;
        String[] fieldParts = fieldPath.split("\\.");
        var currentMap = map;
        try {
            for (int i = 0; i < fieldParts.length; i++) {
                if (currentMap.get(fieldParts[i]) == null) {
                    return null;
                }else if (Map.class.isAssignableFrom(currentMap.get(fieldParts[i]).getClass())) {
                    currentMap = (Map) currentMap.get(fieldParts[i]);
                } else if (List.class.isAssignableFrom(currentMap.get(fieldParts[i]).getClass())) {
                    // if List, retrieve 1st value from it
                    var values = (List) currentMap.get(fieldParts[i]);
                    if (values.isEmpty()) return null;
                    if (!(values.get(0) instanceof Map))  return values;
                    List responseList = new ArrayList();
                    for(var value:values){
                        String fieldSubPath = String.join(".", Arrays.copyOfRange(fieldParts, i+1, fieldParts.length));
                        Object subResponse = findMultiValueInResponseBody(fieldSubPath, (Map)value);
                        if (List.class.isAssignableFrom(subResponse.getClass())) {
                            responseList.addAll((List)subResponse);
                        } else {
                            responseList.add(subResponse);
                        }
                    }
                    return responseList;
                } else {
                    return currentMap.get(fieldParts[i]);
                }
            }
        } catch (Exception e){
            log.error("Error in retrieving {} from response {}", fieldPath, map.toString());
        }
        return null;
    }

}
