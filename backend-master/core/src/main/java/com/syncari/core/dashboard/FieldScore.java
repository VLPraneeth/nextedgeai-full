package com.syncari.core.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Dashboard;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldDataScoreSnapshot;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(value = WidgetSeed.FIELD_SCORE)
public class FieldScore implements WidgetCreator {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;

    @Override
    public void populateData(Dashboard dashBoard) {
        EntityScoreWrapper card = new EntityScoreWrapper();
        Optional<EntityDefinition> entity = Optional.empty();
        if (StringUtils.isNotBlank(dashBoard.getEntityApiName())) {
            entity = getEntityDef(dashBoard.getEntityApiName());
        }
        if(entity.isPresent()) {
            card = repoService.getAllAvgScores(entity.get().getId());
        } else {
            return;
        }
        List<KeyValue> data = new ArrayList<>();
        Map<String, List<FieldDataScoreSnapshot>> byFieldName = card.getFieldScores().stream()
                .collect(Collectors.groupingBy(r -> r.getFieldName()));

        
        for (Entry<String, List<FieldDataScoreSnapshot>> entry : byFieldName.entrySet()) {
            String fieldName = entry.getKey();
            AttributeDefinition attribute = entity.get().getFieldByName(fieldName);
            List<FieldDataScoreSnapshot> scores = entry.getValue();
            KeyValue value = new KeyValue("Api Name", fieldName);
            value.put("rowId", attribute.getId());
            value.put("Display Name", entity.get().getField(fieldName).map(f -> f.getDisplayName()));
            double avgScore = scores.stream().map(r -> Double.valueOf(r.getAverageScore())).reduce(0d,
                    (r1, r2) -> r1 + r2) / (scores.isEmpty() ? 1 : scores.size());
            value.put("Data Fitness Index", avgScore);
            value.put("Rules", scores.stream().map(r -> {
                KeyValue rule = new KeyValue("ruleId", r.getRuleId());
                rule.put("ruleName", r.getRuleName());
                return rule;
            }).collect(Collectors.toList()));
            data.add(value);
        }

        List<KeyValue> config = new ArrayList<>();
        KeyValue conf = new KeyValue("pageInfo", Map.of());
        Map<String, FieldDetail> fieldDetails = new LinkedHashMap<String, FieldDetail>();
        fieldDetails.put("Display Name", new FieldDetail("string", "Display Name", "display_name"));
        fieldDetails.put("Api Name", new FieldDetail("string", "Api Name", "api_name"));
        fieldDetails.put("Data Fitness Index", new FieldDetail("score", "Data Fitness Index", "dfi"));
        fieldDetails.put("Rules", new FieldDetail("list", "Rules", "rules"));
        conf.put("metadata", Map.of("columns", List.of("Display Name", "Api Name", "Data Fitness Index", "Rules"), "fields", fieldDetails));
        config.add(conf);
        dashBoard.getWidget(WidgetSeed.FIELD_SCORE).ifPresent(widget -> {
            widget.populateData(data);
            widget.getContents().get(0).setConfig(config);
        });
    }
    
    private Optional<EntityDefinition> getEntityDef(String name) {
        return schemaService.getSyncariEntityByName(name);
    }
}

@Data
@AllArgsConstructor
class FieldDetail {
    String dataType;
    String label;
    String id;
}
