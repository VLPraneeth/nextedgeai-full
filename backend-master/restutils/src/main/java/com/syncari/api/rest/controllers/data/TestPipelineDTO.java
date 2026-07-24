package com.syncari.api.rest.controllers.data;

import lombok.Data;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.syncari.core.model.PipelineTestWebhook;

@Data
public class TestPipelineDTO {
    String start;
    String end;
    String limit;
    Map<String, List<String>> recordIds;
    Map<String, PipelineTestWebhook> webhook;

    /**
     * Workaround for UI not splitting comma separated recordIds
     * @return
     */
    public Map<String, List<String>> getRecordIds(){
        if(recordIds==null) return Map.of();
        Map<String, List<String>> flattened = new HashMap<>();
        recordIds.forEach((key, ids)->{
            flattened.put(key, ids.stream().flatMap(i-> Arrays.asList(i.split(",")).stream()).collect(Collectors.toList()));
        });
        return flattened;
    }
}
