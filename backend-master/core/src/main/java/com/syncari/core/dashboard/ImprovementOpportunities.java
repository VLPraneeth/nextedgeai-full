package com.syncari.core.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.Dashboard;
import com.syncari.core.model.misc.DataScoreCard;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(value = WidgetSeed.IMPROVEMENT_OPPORTUNITIES)
public class ImprovementOpportunities implements WidgetCreator {
    @Autowired
    EntityRepoService repoService;
    @Autowired
    SchemaService schemaService;

    @Override
    public void populateData(Dashboard dashBoard) {
        List data = new ArrayList<>();
        KeyValue e = new KeyValue("id", UUID.randomUUID().toString());
        // TODO set links
        dashBoard.getWidget(WidgetSeed.IMPROVEMENT_OPPORTUNITIES).ifPresent(widget -> {
            List<DataScoreCard> cards = repoService.getAllScoreCard();
            cards.forEach(card -> {
                KeyValue val = new KeyValue("id", UUID.randomUUID().toString());
                val.put("card", card);
                data.add(val);
            });
            widget.populateData(WidgetType.dataScoreLineItems.name(), data);
        });
        
    }
}
