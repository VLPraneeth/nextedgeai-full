package com.syncari.core.dashboard;

import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Dashboard;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(value = WidgetSeed.DATA_FITNESS_OVERTIME)
public class DataFitnessOvertime implements WidgetCreator {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;

    @Override
    public void populateData(Dashboard dashBoard) {
        List<KeyValue> score = new ArrayList<>();
        KeyValue e = new KeyValue("id", UUID.randomUUID().toString());
        switch (dashBoard.getName()) {
        case DashboardSeed.DQS_OVERVIEW:
            dashBoard.getWidget(WidgetSeed.DATA_FITNESS_OVERTIME).ifPresent(widget -> {
                int rangeInDays = 30;
                Map<String, Integer> result = repoService.getOverallDfiTrend(rangeInDays);
                result.forEach((k, v) -> {
                    KeyValue val = new KeyValue("id", UUID.randomUUID().toString());
                    val.put("label", k);
                    val.put("x", k);
                    val.put("y", v);
                    score.add(val);
                });
                widget.populateData("trend", List.of(score));
            });
            break;
        default:
            populate(dashBoard, score, e, dashBoard.getEntityApiName());
            break;
        }
    }

    private void populate(Dashboard dashBoard, List<KeyValue> score, KeyValue e, String entity) {
        dashBoard.getWidget(WidgetSeed.DATA_FITNESS_OVERTIME).ifPresent(widget -> {
            int rangeInDays = 30;
            Map<String, Integer> result = repoService.getDfiTrend(getEntityDef(entity).getId(), rangeInDays);
            result.forEach((k, v) -> {
                KeyValue val = new KeyValue("id", UUID.randomUUID().toString());
                val.put("label", k);
                val.put("x", k);
                val.put("y", v);
                score.add(val);
            });
            widget.populateData("trend", List.of(score));
        });
    }

    private EntityDefinition getEntityDef(String name) {
        return schemaService.getSyncariEntityByName(name).orElseThrow(
                () -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", "Name", name)));
    }
}
