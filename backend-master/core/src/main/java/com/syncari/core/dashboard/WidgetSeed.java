package com.syncari.core.dashboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.model.misc.Widget;
import com.syncari.core.model.misc.WidgetContent;
import com.syncari.core.model.misc.WidgetLayout;
import com.syncari.core.model.misc.WidgetType;
import com.syncari.utils.KeyValue;

public class WidgetSeed {
    public static final String ENTITY_DATA_QUALITY_BREAKDOWN = "entityDataQualityBreakdown";
    public static final String IMPROVEMENT_OPPORTUNITIES = "improvementOpportunities";
    public static final String DATA_FITNESS_OVERTIME = "dataFitnessOvertime";
    public static final String OVERALL_FITNESS = "overallFitness";
    public static final String FIELD_SCORE = "fieldScore";
    private static Map<String, Widget> widgetMap = new HashMap<>();

    static {
        widgetMap.put(OVERALL_FITNESS, getOverallFitness());
        widgetMap.put(DATA_FITNESS_OVERTIME, getDataFitnessOvertime());
        widgetMap.put(IMPROVEMENT_OPPORTUNITIES, getImprovementOpportunities());
        widgetMap.put(ENTITY_DATA_QUALITY_BREAKDOWN, getEntityDataQualityBreakdown());
        widgetMap.put(FIELD_SCORE, getFieldScore());
    }

    public static Widget populate(Widget d){
        Widget fromSeed = WidgetSeed.get(d.getName());
        if(fromSeed != null) {
            d.setTitle(fromSeed.getTitle()).setContents(fromSeed.getContents())
            .setLayout(fromSeed.getLayout()).setLoadingText(fromSeed.getLoadingText());
        }
        return d;
    }

    public static Widget get(String name) {
        return widgetMap.get(name);
    }

    public static Widget getOverallFitness() {
        WidgetContent c1 = new WidgetContent(WidgetType.hstack);
        List<WidgetContent> contents = new ArrayList<>();
        WidgetContent gauge = new WidgetContent(WidgetType.gauge, "fitnessGauge");
        WidgetContent stack = new WidgetContent(WidgetType.stack, "fitnessChange");
        List<WidgetContent> stackContents = new ArrayList<>();
        WidgetContent badge = new WidgetContent(WidgetType.trendBadge, "fitnessBadge");
        stackContents.add(badge);
        stack.setContents(stackContents);
        contents.add(gauge);
        contents.add(stack);
        c1.setContents(contents);
        return new Widget()
                .setName(OVERALL_FITNESS)
                .setTitle("Overall Data Fitness Index")
                .setLoadingText("Loading Overall Data Fitness Index")
                .addContent(c1)
                .setLayout(new WidgetLayout().setX(0).setY(0).setH(2).setW(6));
    }
    public static Widget getDataFitnessOvertime() {
        WidgetContent c1 = new WidgetContent(WidgetType.stack);
        List<WidgetContent> contents = new ArrayList<>();
        WidgetContent lineChart = new WidgetContent(WidgetType.lineChart, "trend");
        contents.add(lineChart);
        c1.setContents(contents);
        return new Widget()
                .setName(DATA_FITNESS_OVERTIME)
                .setTitle("Data Fitness Over Time")
                .setLoadingText("Loading Data Fitness Over Time")
                .addContent(c1)
                .setLayout(new WidgetLayout().setX(7).setY(0).setH(2).setW(10));
    }
    public static Widget getImprovementOpportunities() {
        List<WidgetContent> contents = new ArrayList<>();
        WidgetContent lineItems = new WidgetContent(WidgetType.dataScoreLineItems, WidgetType.dataScoreLineItems.name());
        contents.add(lineItems);

        WidgetContent stack = new WidgetContent(WidgetType.stack);
        List<KeyValue> config = new ArrayList<KeyValue>();
        config.add(new KeyValue("scrollOverflow", true));
        stack.setConfig(config);
        stack.setContents(contents);

        return new Widget()
                .setName(IMPROVEMENT_OPPORTUNITIES)
                .setTitle("Improvement Opportunities")
                .setLoadingText("Loading Improvement Opportunities")
                .addContent(stack)
                .setLayout(new WidgetLayout().setX(0).setY(2).setH(3).setW(6).setMinH(2).setMaxH(5));
    }
    public static Widget getEntityDataQualityBreakdown() {
        WidgetContent stack = new WidgetContent(WidgetType.stack);
        List<WidgetContent> contents = new ArrayList<>();
        WidgetContent entityBreakdownChart = new WidgetContent(WidgetType.entityBreakdown, "dataQualityBreakdown");
        contents.add(entityBreakdownChart);
        stack.setContents(contents);
        stack.setConfig(List.of(KeyValue.of("fill", true)));
        return new Widget()
                .setName(ENTITY_DATA_QUALITY_BREAKDOWN)
                .setTitle("Entity Data Quality Breakdown")
                .setLoadingText("Loading Entity Data Quality Breakdown")
                .addContent(stack)
                .setLayout(new WidgetLayout().setX(6).setY(2).setH(3).setW(10).setMinH(2).setMaxH(5));
    }
    public static Widget getFieldScore() {
        WidgetContent table = new WidgetContent(WidgetType.table);
        return new Widget()
                .setName(FIELD_SCORE)
                .setTitle("Field Score")
                .addContent(table)
                .setLoadingText("Loading Field Score")
                .setLayout(new WidgetLayout().setX(0).setY(2).setH(3).setW(16));

    }
}
