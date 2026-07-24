package com.syncari.core.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.Dashboard;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(value = WidgetSeed.ENTITY_DATA_QUALITY_BREAKDOWN)
public class EntityDataQualityBreakdown implements WidgetCreator {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;

    @Override
    public void populateData(Dashboard dashBoard) {
        List<KeyValue> result = new ArrayList<>();
        KeyValue e = new KeyValue("id", UUID.randomUUID().toString());
        dashBoard.getWidget(WidgetSeed.ENTITY_DATA_QUALITY_BREAKDOWN).ifPresent(widget -> {
            Map<String, Integer> scoreMap = repoService.getEntityScoreMap();
            scoreMap.forEach((k, v) -> {
                KeyValue val = new KeyValue("id", UUID.randomUUID().toString());
                val.put("label", k);
                val.put("value", v);
                result.add(val);
            });
            widget.populateData("dataQualityBreakdown", result);
        });
    }
}
