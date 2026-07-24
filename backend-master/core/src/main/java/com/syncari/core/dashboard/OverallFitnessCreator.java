package com.syncari.core.dashboard;

import static com.syncari.utils.I18n.i18n;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.Dashboard;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(value = WidgetSeed.OVERALL_FITNESS)
public class OverallFitnessCreator implements WidgetCreator {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;

    @Override
    public void populateData(Dashboard dashBoard) {
        switch (dashBoard.getName()) {
        case DashboardSeed.DQS_OVERVIEW:
            populate(dashBoard, Optional.empty());
            break;
        default:
            populate(dashBoard, Optional.of(dashBoard.getEntityApiName()));
            break;
        }
    }

    private void populate(Dashboard dashBoard, Optional<String> entity) {
        KeyValue e = new KeyValue("id", UUID.randomUUID().toString());
        dashBoard.getWidget(WidgetSeed.OVERALL_FITNESS).ifPresent(widget -> {
            List<KeyValue> score = new ArrayList<>();
            int scoreVal = getTodaysScore(entity);
            int scoreValOld = getLastMonthScore(entity);
            e.put("label", repoService.getScoreLabel(scoreVal));
            e.put("value", scoreVal);
            score.add(e);
            widget.populateData("fitnessGauge", score);
            List<KeyValue> badge = new ArrayList<>();
            String direction = getDirection(scoreVal, scoreValOld).name();
            KeyValue e1 = new KeyValue("id", UUID.randomUUID().toString());
            if (TrendDirection.neutral.name().equalsIgnoreCase(direction)) {
                e1.put("value", StringUtils.capitalize("No change in last 30 days"));
            } else {
                e1.put("value", StringUtils.capitalize(direction + " " + getPercent(scoreVal, scoreValOld) + "% last 30 days"));
            }
            
            e1.put("trendDirection", direction);
            badge.add(e1);
            widget.populateData("fitnessBadge", badge);
        });
    }

    private int getTodaysScore(Optional<String> entity) {
        if (entity.isPresent()) {
            EntityScoreWrapper entityScore = repoService.getTop3AvgScores(getEntityDef(entity.get()).getId());
            return entityScore.getEntityScore().getScore();
        }
        return repoService.getOverallScore();
    }

    private int getLastMonthScore(Optional<String> entity) {
        if (entity.isPresent()) {
            EntityScoreWrapper entityScore = repoService.getAvgScores(getEntityDef(entity.get()),
                    Instant.now().minus(30, ChronoUnit.DAYS));
            return entityScore.getEntityScore().getScore();
        }
        return repoService.getOverallScore(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    private EntityDefinition getEntityDef(String name) {
        return schemaService.getSyncariEntityByName(name).orElseThrow(
                () -> new SyncariValidationException(String.format(i18n("not_found"), "Entity", "Name", name)));
    }

    private TrendDirection getDirection(int newVal, int oldVal) {
        if (newVal == oldVal)
            return TrendDirection.neutral;
        if (newVal > oldVal)
            return TrendDirection.up;
        return TrendDirection.down;
    }
    
    private int getPercent(int newVal, int oldVal) {
        if (oldVal == 0 && newVal == 0) return 0;
        if (oldVal == 0 && newVal != 0)
            return newVal;
        if (newVal == oldVal) return 0;
        return Math.abs((int) (((newVal - oldVal) * 1.0f / oldVal) * 100));
    }
}
