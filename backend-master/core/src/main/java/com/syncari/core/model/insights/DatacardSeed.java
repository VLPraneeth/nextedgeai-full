package com.syncari.core.model.insights;

import com.syncari.core.model.insights.dataset.Sort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Supplier;

@Slf4j
public class DatacardSeed {

    private static final Map<String, Supplier<Datacard>> datacardMap = new HashMap<>();

    static {
        datacardMap.put("quarterlyClosedPipelineRevenueByType", () -> quarterlyClosedPipelineRevenueByTypeSeed());
        datacardMap.put("annualRecurringRevenue", () -> yearlyClosedPipelineRevenueSeed());
        datacardMap.put("leadCountBySource", () -> leadCountBySource());
        datacardMap.put("nextFewQuaterOpenPipelines", () -> nextFewQuarterOpenPipelines());
        datacardMap.put("existingCustomerCount", () -> existingCustomerCount());
        datacardMap.put("allOpenPipelineByType", () -> allOpenPipelineByOppType());
        datacardMap.put("sqlLeadCountByOwner", () -> sqlleadCountByOwner());
        datacardMap.put("salesFunnel", () -> salesFunnel());
        datacardMap.put("top10CustomersByRevenue", () -> top10CustByRev());
        datacardMap.put("mqlCountInQuarter", () -> mqlCountInCurrentQuarter());
        datacardMap.put("openEscalatedTicketCount", () -> openEscalatedTicketCount());
        datacardMap.put("openTicketsCountByAccount", () -> openTicketsCountByAccount());
        datacardMap.put("allOpenPipelineTotal", () -> allOpenPipelineSumSeed());
        datacardMap.put("openTicketsByPriority", () -> allOpenTicketsByPriority());
        datacardMap.put("trendOfIssuesResolvedIn24hours", () -> trendClosedTicket24Hours());
        datacardMap.put("trendOfIssuesResolvedIn7Days", () -> trendClosedTicket7days());
        datacardMap.put("openTicketsInAccountsWithOpenPipeline", () -> openTicketWithOpenPipeline());
        datacardMap.put("openRenewalLogoCount", () -> openRenewalLogoCountSeed());
        datacardMap.put("openRenewals", () -> openRenewalsSeed());
        datacardMap.put("openTicketsAccountforOpenPipeline", () -> openTicketsAccountforOpenPipeline());
        datacardMap.put("upcomingRenewalDates", () -> upcomingRenewalSeed());
        datacardMap.put("revenueChurnByQuarter", () -> revenueChurnByQuarter());
        datacardMap.put("userGrowth", () -> userGrowthSeed());
        datacardMap.put("avgRevenueForAllAccounts", () -> avgRevenueForAllAccounts());
        datacardMap.put("allOpenPipelineNewCount", () -> allOpenPipelineCountSeedNew());
        datacardMap.put("quarterlyClosedPipelineRevenue", () -> quarterlyClosedPipelineRevenueSeed());

        // Migrated datacards
        datacardMap.put("quarterlyClosedPipelineRevenueDC", () -> quarterlyClosedPipelineRevenueDCSeed());
        datacardMap.put("annualRecurringRevenueDC", () -> yearlyClosedPipelineRevenueDCSeed());
        datacardMap.put("nextFewQuaterOpenPipelinesDC", () -> nextFewQuarterOpenPipelinesDC());
        datacardMap.put("allOpenPipelineByTypeDC", () -> allOpenPipelineByOppTypeDC());
        datacardMap.put("salesFunnelDC", () -> salesFunnelDC());
        datacardMap.put("openRenewalLogoCountDC", () -> openRenewalLogoCountDC());
        datacardMap.put("upcomingRenewalDatesDC", () -> upcomingRenewalSeedDC());
        datacardMap.put("openRenewalsDC", () -> openRenewalsSeedDC());
        datacardMap.put("quarterlyClosedPipelineRevenueByTypeDC", () -> quarterlyClosedPipelineRevenueByTypeDC());
        datacardMap.put("avgRevenueForAllAccountsDC", () -> avgRevenueForAllAccountsDC());
        datacardMap.put("existingCustomerCountDC", () -> existingCustomerCountDC());
        datacardMap.put("top10CustomersByRevenueDC", () -> top10CustomersByRevenueDC());
        datacardMap.put("revenueChurnByQuarterDC", () -> revenueChurnByQuarterDC());
        datacardMap.put("openTicketsCountByAccountDC", () -> openTicketsCountByAccountDC());


        // Migrated Datacards
        datacardMap.put("openEscalatedTicketCountDC", () -> openEscalatedTicketCountDC());
        datacardMap.put("accountsByOpenTicketCountDC", () -> accountsByOpenTicketCountDC());
        datacardMap.put("openTicketsByPriorityDC", () -> openTicketsByPriorityDC());
        datacardMap.put("leadsBySourceDC", () -> leadsBySourceDC());
        datacardMap.put("mqlCountInQuarterDC", () -> mqlCountInQuarterDC());
        datacardMap.put("sqlCountByOwnerDC", () -> sqlCountByOwnerDC());
        datacardMap.put("userGrowthDC", () -> userGrowthDC());
        datacardMap.put("trendOfIssuesResolvedIn7DaysDC", () -> trendOfIssuesResolvedIn7DaysDC());
        datacardMap.put("trendOfIssuesResolvedIn24HoursDC", () -> trendOfIssuesResolvedIn24HoursDC());
        datacardMap.put("openTicketsAccountforOpenPipelineDC", () -> openTicketsAccountforOpenPipelineDC());

        // Missing ones causing error
        datacardMap.put("allOpenPipelineCount", () -> allOpenPipelineCountSeedNew());
        datacardMap.put("openTicketWithOpenPipelineDC", () -> openTicketsAccountforOpenPipelineDC());
        datacardMap.put("allOpenPipelineTotalDC", () -> allOpenPipelineSumSeedDC());


    }

    public static Datacard get(String name) {
        Datacard datacardFromSeed = datacardMap.get(name).get();
        if(datacardFromSeed == null) return null;
        return datacardFromSeed.makeCopy();
    }

    public static Datacard populateDataCard(Datacard datacard){
        try {
            Datacard fromSeed = DatacardSeed.get(datacard.getName());
            if (fromSeed == null) return datacard;
            Map<String, String> vizConfDatasetMap = new HashMap<>();
            Map<String, DateFilter> vizConfDatefilterMap = new HashMap<>();
            datacard.getContents().forEach(viz -> buildDatasetMapToSet(viz.getConfig(), vizConfDatasetMap));
            datacard.getContents().forEach(viz -> buildDateFilterMap(viz.getConfig(), vizConfDatefilterMap));
            // setdatasetid
            fromSeed.getContents().forEach(viz -> setDatasetIdToVizConfig(viz.getConfig(), vizConfDatasetMap));
            //setdatefilter
            fromSeed.getContents().forEach(viz -> setDatefilterToVizConfig(viz.getConfig(), vizConfDatefilterMap));
            datacard.setName(fromSeed.getName())
                    .setDisplayName(fromSeed.getDisplayName())
                    .setDescription(fromSeed.getDescription())
                    .setContents(fromSeed.getContents())
                    .setConfiguration(fromSeed.getConfiguration())
                    .setSeeded(true);
        } catch (Exception e){
            log.error(String.format("Unable to load datacard %s. Error: %s", datacard.getName(), e.getMessage()), e);
            datacard.setErrorMsg(e.getMessage());
        }
        return datacard;
    }

    private static Map<String, String> buildDatasetMapToSet(VizConfig vizConfig, Map<String, String> vizConfigDSMap) {
        vizConfigDSMap.put(vizConfig.getName(),vizConfig.getDatasetId());
        if (null != vizConfig.getChildVizConfig()){
            vizConfigDSMap = buildDatasetMapToSet(vizConfig.getChildVizConfig(), vizConfigDSMap);
        }
        return vizConfigDSMap;
    }


    private static Map<String, DateFilter> buildDateFilterMap(VizConfig vizConfig, Map<String, DateFilter> vizConfigDRMap) {
        vizConfigDRMap.put(vizConfig.getName(),vizConfig.getDateFilter());
        return vizConfigDRMap;
    }

    private static Map<String, String> setDatasetIdToVizConfig(VizConfig vizConfig, Map<String, String> vizConfigDSMap) {
        log.info("Dataset value in map is {}", vizConfigDSMap.get(vizConfig.getName()));
        vizConfig.setDatasetId(vizConfigDSMap.get(vizConfig.getName()));
        if (null != vizConfig.getChildVizConfig()){
            setDatasetIdToVizConfig(vizConfig.getChildVizConfig(), vizConfigDSMap);
        }
        return vizConfigDSMap;
    }
    private static Map<String, DateFilter> setDatefilterToVizConfig(VizConfig vizConfig, Map<String, DateFilter> vizConfigDFMap) {
        log.info("Datefilter value in map from db is {}, use seeded one for datacard {}", vizConfigDFMap.get(vizConfig.getName()),vizConfig.getName());
        if (null != vizConfigDFMap.get(vizConfig.getName())){
            vizConfig.setDateFilter(vizConfigDFMap.get(vizConfig.getName()));
        }
        return vizConfigDFMap;
    }


    private static Datacard quarterlyClosedPipelineRevenueSeed(){
        return new Datacard().setName("quarterlyClosedPipelineRevenue")
                .setDisplayName("Sales by Quarter")
                .setDescription("Amount in $ of closed won opportunities by quarter")
                .setContents(List.of(getQuarterlyClosedPipelineRevenueBarChart()))
                .setConfiguration(new DatacardConfig().setDateRange(getCurrentYearDateRange()))
                .setSeeded(true);
    }

    private static Datacard quarterlyClosedPipelineRevenueDCSeed(){
        return new Datacard().setName("quarterlyClosedPipelineRevenueDC")
                .setDisplayName("Sales by Quarter")
                .setContents(List.of(getQuarterlyClosedPipelineRevenueBarChartNew()))
                .setDescription("Amount in $ of closed won opportunities by quarter")
                .setSeeded(true);
    }
    private static Visualization getQuarterlyClosedPipelineRevenueBarChartNew(){
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName")));

        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount in USD").setDataType("integer");

        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Amount in USD").setDisplayFormat("currency");

        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));

        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        return new Visualization().setName("revenueBar")
                .setDescription("Bar chart for Quarterly Closed Pipeline Revenue")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        .setPipelineDependencies(deps).setName("revenueBar").setColumns(List.of(quarter, total)));
    }

    private static Visualization getQuarterlyClosedPipelineRevenueBarChart(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount in USD").setDataType("integer");

        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Amount in USD").setDisplayFormat("currency");

        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));

        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("revenueBar")
                .setDescription("Bar chart for Quarterly Closed Pipeline Revenue")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))))
                        .setSortList(List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true)))
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(getCurrentYearDateRange()))
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(total, quarter)).setName("revenueBar"));
    }

    private static DateRange getDateRangefromEpochToNow(){
        return new DateRange(convertToLocalDateViaInstant(new Date(Instant.EPOCH.toEpochMilli())),
                convertToLocalDateViaInstant( new Date(Instant.now().toEpochMilli())));
    }
    private static LocalDateTime convertToLocalDateViaInstant(Date dateToConvert) {
        return dateToConvert.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private static Datacard quarterlyClosedPipelineRevenueByTypeSeed(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate.minusMonths(12),currentLocalDate);
        return new Datacard().setName("quarterlyClosedPipelineRevenueByType")
                .setDisplayName("Sales By Type")
                .setDescription("Quarterly Closed Pipeline Amount By Type")
                .setContents(List.of(getQuarterlyClosedPipelineRevenueByTypesLineChart()))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Datacard yearlyClosedPipelineRevenueSeed(){
        return new Datacard().setName("annualRecurringRevenue")
                .setDisplayName("Current Year Sales")
                .setDescription("Amount in $ of closed won opportunities this year")
                .setContents(List.of((annualRecurringRevMetricChart())))
                .setSeeded(true);
    }

    private static Datacard yearlyClosedPipelineRevenueDCSeed(){
        return new Datacard().setName("annualRecurringRevenue")
                .setDisplayName("Current Year Sales")
                .setDescription("Amount in $ of closed won opportunities this year")
                .setContents(List.of((annualRecurringRevMetricChartNew())))
                .setSeeded(true);
    }

    private static Visualization annualRecurringRevMetricChartNew(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Total").setDataType("integer");
        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total amount in closed opportunity for every type").setDisplayFormat("currency");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName")));

        return new Visualization().setName("arr")
                .setDescription("Metric chart for current year closed pipeline revenue sum")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(total)).setName("arr"));

    }

    private static Visualization getQuarterlyClosedPipelineRevenueByTypesLineChart(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Total").setDataType("integer");
        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount in closed opportunity for every type").setDisplayFormat("currency");

        NoQueryFunction opptypeNoQueryFunction = new NoQueryFunction();
        opptypeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("opptype").setDataType("string");

        QueryField oppType = new SimpleQField()
                .setQueryFunction(opptypeNoQueryFunction)
                .setDescription("Type of opportunity")
                .setDisplayFormat("string");
        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate.minusMonths(12),currentLocalDate);

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        return new Visualization().setName("revenueLine")
                .setDescription("Line chart for Quarterly Closed Pipeline Revenue By Type")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        .setSeries(List.of(oppType))
                        .setPipelineDependencies(deps)
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear")),
                                new AggregateConfig().setAggregateField(new QField().setName("type"))))
                        .setSortList(List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true)))
                        .setColumns(List.of(total, quarter, oppType)).setName("revenueLine")
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range)));
    }

    private static Datacard quarterlyClosedPipelineRevenueByTypeDC(){
        return new Datacard().setName("quarterlyClosedPipelineRevenueByTypeDC")
                .setDisplayName("Sales By Type")
                .setDescription("Quarterly Closed Pipeline Amount By Type")
                .setContents(List.of(quarterlyClosedPipelineRevenueByTypesLineChartDC()))
                .setSeeded(true);
    }

    private static Visualization quarterlyClosedPipelineRevenueByTypesLineChartDC(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Total").setDataType("integer");
        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount in closed opportunity for every type").setDisplayFormat("currency");

        NoQueryFunction opptypeNoQueryFunction = new NoQueryFunction();
        opptypeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("opptype").setDataType("string");

        QueryField oppType = new SimpleQField()
                .setQueryFunction(opptypeNoQueryFunction)
                .setDescription("Type of opportunity")
                .setDisplayFormat("string");
        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        return new Visualization().setName("quarterlyClosedPipelineRevenueByTypeDC")
                .setDescription("Line chart for Quarterly Closed Pipeline Revenue By Type")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        .setSeries(List.of(oppType))
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(total, quarter, oppType)).setName("quarterlyClosedPipelineRevenueByTypeDC"));
    }

    private static Visualization annualRecurringRevMetricChart(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Total").setDataType("integer");
        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total amount in closed opportunity for every type").setDisplayFormat("currency");

        NoQueryFunction fiscalyearNoQueryFunction = new NoQueryFunction();
        fiscalyearNoQueryFunction.setColumns(List.of(new QField("fiscalyear", QField.Type.COLUMN)))
                .setAlias("fiscalyear").setDataType("integer");

        QueryField fiscalYear = new SimpleQField()
                .setQueryFunction(fiscalyearNoQueryFunction)
                .setDescription("Fiscal year")
                .setDisplayFormat("integer");

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",fiscalYear.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", currentYear)
        );

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName")));

        return new Visualization().setName("arr")
                .setDescription("Metric chart for current year closed pipeline revenue sum")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(fiscalYear.getAlias()))))
                        .setPredicate(predicateCondition)
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(total)).setName("arr"));

    }

    private static Datacard leadCountBySource(){
        return new Datacard().setName("leadCountBySource")
                .setDisplayName("Marketing Attribution Funnel")
                .setDescription("Distribution of leads by source")
                .setContents(List.of((leadBySourceTable())))
                .setSeeded(true);
    }

    private static Visualization leadBySourceTable(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("syncariid", QField.Type.COLUMN)))
                .setAlias("Lead Count").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total leads for each source type").setDisplayFormat("number");

        NoQueryFunction leadSourceNoQueryFunction = new NoQueryFunction();
        leadSourceNoQueryFunction.setColumns(List.of(new QField("leadsource", QField.Type.COLUMN)))
                .setAlias("Lead Source").setDataType("text");

        QueryField source = new SimpleQField()
                .setQueryFunction(leadSourceNoQueryFunction)
                .setDescription("Lead Source")
                .setDisplayFormat("text");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("lead", List.of("Email", "leadSource", "Status")));

        return new Visualization().setName("leadsBySourceTable")
                .setDescription("Table for lead count by source")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(source.getAlias()))))
                        .setSortList(List.of(new Sort(new QField().setName(count.getAlias()), false)))
                        .setColumns(List.of(source, count))
                        .setPipelineDependencies(deps)
                        .setName("leadCountBySource"));
    }

    private static Datacard allOpenPipelineCountSeed(){
        return new Datacard().setName("allOpenPipelineCount")
                .setDisplayName("All Open Pipeline Count")
                .setDescription("Count of all open pipelines")
                .setContents(List.of(((openPipelineCount()))))
                .setSeeded(true);
    }
    private static Visualization openPipelineCount(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("openPipelineCount").setDataType("date");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total count of all open pipelines").setDisplayFormat("number");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("allOpenPipelineCount")
                .setDescription("All open pipelines count")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(count))
                        .setPipelineDependencies(deps)
                        .setName("allOpenPipelineCount"));
    }

    private static Datacard allOpenPipelineCountSeedNew(){
        return new Datacard().setName("allOpenPipelineNewCount")
                .setDisplayName("All Open Pipeline Count")
                .setDescription("Count of all open pipelines")
                .setContents(List.of(((openPipelineCountNew()))))
                .setSeeded(true);
    }
    private static Visualization openPipelineCountNew(){

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("allOpenPipelineNewCount")
                .setDescription("All open pipelines count")
                .setType(VizType.METRIC)
                .setDisplayFormat("number")
                .setConfig(new MetricVizConfig()
                        .setPipelineDependencies(deps)
                        .setName("allOpenPipelineNewCount"));
    }

    private static Datacard allOpenPipelineSumSeed(){
        return new Datacard().setName("allOpenPipelineTotal")
                .setDisplayName("Open Pipeline $")
                .setDescription("Total amount of all open pipeline opportunities")
                .setContents(List.of(((openPipelineSum()))))
                .setSeeded(true);
    }
    private static Visualization openPipelineSum(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("openPipelineTotal").setDataType("integer");
        QueryField sum = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total sum of all open pipelines").setDisplayFormat("currency");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("allOpenPipelineTotal")
                .setDescription("All open pipelines total")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(sum))
                        .setPipelineDependencies(deps)
                        .setName("allOpenPipelineTotal"));
    }

    private static Datacard allOpenPipelineSumSeedDC(){
        return new Datacard().setName("allOpenPipelineTotalDC")
                .setDisplayName("Open Pipeline $")
                .setDescription("Total amount of all open pipeline opportunities")
                .setContents(List.of(((openPipelineSumDC()))))
                .setSeeded(true);
    }
    private static Visualization openPipelineSumDC(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("openPipelineTotal").setDataType("integer");
        QueryField sum = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total sum of all open pipelines").setDisplayFormat("currency");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("allOpenPipelineTotalDC")
                .setDescription("All open pipelines total")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(sum))
                        .setPipelineDependencies(deps)
                        .setName("allOpenPipelineTotalDC"));
    }

    private static Datacard allOpenTicketsByPriority(){
        return new Datacard().setName("openTicketsByPriority")
                .setDisplayName("Issues By Priority")
                .setDescription("The count of open issues by priority")
                .setContents(List.of(((openTicketsByPriority()))))
                .setConfiguration(new DatacardConfig().setDateRange(get6monthsDateRangenow()))
                .setSeeded(true);
    }
    private static Visualization openTicketsByPriority(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("priority", QField.Type.COLUMN)))
                .setAlias("Tickets Count").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total number of open tickets by priority").setDisplayFormat("number");

        NoQueryFunction priorityNoQueryFunction = new NoQueryFunction();
        priorityNoQueryFunction.setColumns(List.of(new QField().setName("priority").setDataType("string")))
                .setAlias("Priority").setDataType("text");

        QueryField priorityField = new SimpleQField().setQueryFunction(priorityNoQueryFunction).setDisplayFormat("text")
                .setDescription("Open tickets priority");

        NoQueryFunction createddateNoQueryFunction = new NoQueryFunction();
        createddateNoQueryFunction.setColumns(List.of(new QField().setName("createddate").setDataType("date")))
                .setAlias("createddate").setDataType("date");


        QueryField dateField = new SimpleQField().setQueryFunction(createddateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        NoQueryFunction isClosedNoQueryFunction = new NoQueryFunction();
        isClosedNoQueryFunction.setColumns(List.of(new QField("isClosed", QField.Type.COLUMN)))
                .setAlias("isClosed").setDataType("boolean");

        QueryField isClosed = new SimpleQField()
                .setQueryFunction(isClosedNoQueryFunction)
                .setDescription("Is Closed")
                .setDisplayFormat("boolean");

        Map<String, Object> pred1 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",isClosed.getAlias()),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", true)
        );
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        return new Visualization().setName("openTicketsByPriority")
                .setDescription("Open tickets by priority")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(priorityField).setYAxis(List.of(count))
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName("priority"))))
                        .setSortList(List.of(new Sort(new QField().setName("Tickets Count"), false)))
                        .setPredicate(pred1)
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(get6monthsDateRangenow()))
                        .setColumns(List.of(count, priorityField))
                        .setPipelineDependencies(deps)
                        .setName("openTicketsByPriority"));
    }


    private static DateRange get6monthsDateRangenow(){
        return new DateRange(LocalDateTime.now().minusMonths(6),
                LocalDateTime.now());
    }

    private static DateRange getCurrentYearDateRange(){
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstDay = now.with(TemporalAdjusters.firstDayOfYear());
        LocalDateTime lastDay = now.with(TemporalAdjusters.lastDayOfYear());
        return new DateRange(firstDay, lastDay);
    }



    private static Datacard nextFewQuarterOpenPipelines(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12));
        return new Datacard().setName("nextFewQuaterOpenPipelines")
                .setDisplayName("Pipeline By Close Date")
                .setDescription("Pipeline By Close Date")
                .setContents(List.of(((nextFewQuarterOpenPipelinesQuery()))))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization nextFewQuarterOpenPipelinesQuery(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("integer");
        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount in closed opportunity").setDisplayFormat("currency");

        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12));
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("nextFewQuaterOpenPipelines")
                .setDescription("Bar chart for Next 3 Quarterly Open Pipeline Revenue")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        //.setSeries(List.of(total))
                        .setStacking(StackingType.normal)
                        .setPipelineDependencies(deps)
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))))
                        .setSortList(List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true)))
                        .setColumns(List.of(total, quarter)).setName("nextFewQuaterOpenPipelines")
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range)));
    }

    private static Datacard nextFewQuarterOpenPipelinesDC(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12));
        return new Datacard().setName("nextFewQuaterOpenPipelinesDC")
                .setDisplayName("Pipeline By Close Date")
                .setDescription("Pipeline By Close Date")
                .setContents(List.of(((nextFewQuarterOpenPipelinesQueryNew()))))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization nextFewQuarterOpenPipelinesQueryNew(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("integer");
        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount in closed opportunity").setDisplayFormat("currency");

        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("nextFewQuaterOpenPipelinesDC")
                .setDescription("Bar chart for Next 3 Quarterly Open Pipeline Revenue")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(quarter).setYAxis(List.of(total)).setName("nextFewQuaterOpenPipelinesDC")
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(total, quarter)));
    }

    private static LocalDateTime getCurrentQuarterFirsDate(){
        LocalDateTime localDate = LocalDateTime.now();
        return localDate.with(localDate.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());
    }

    private static Datacard existingCustomerCount(){
        return new Datacard().setName("existingCustomerCount")
                .setDisplayName("Customer Count")
                .setDescription("Number of closed won customers")
                .setContents(List.of(((existingCustomerNumber()))))
                .setSeeded(true);
    }

    private static Visualization existingCustomerNumber(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("customercount").setDataType("integer")
                .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total number of accounts").setDisplayFormat("number");

        NoQueryFunction accidNoQueryFunction = new NoQueryFunction();
        accidNoQueryFunction.setColumns(List.of(new QField().setName("accountid").setDataType("string")))
                .setAlias("accountid").setDataType("string");
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("AccountId", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("existingCustomerNumber")
                .setDescription("Metric chart for number of existing customers")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig().setColumns(List.of(count))
                        .setName("allCustomerCount").setPipelineDependencies(deps));
    }

    private static Datacard existingCustomerCountDC(){
        return new Datacard().setName("existingCustomerCountDC")
                .setDisplayName("Customer Count")
                .setDescription("Number of closed won customers")
                .setContents(List.of(((existingCustomerNumberDC()))))
                .setSeeded(true);
    }

    private static Visualization existingCustomerNumberDC(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("customercount").setDataType("integer")
                .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total number of accounts").setDisplayFormat("number");

        NoQueryFunction accidNoQueryFunction = new NoQueryFunction();
        accidNoQueryFunction.setColumns(List.of(new QField().setName("accountid").setDataType("string")))
                .setAlias("accountid").setDataType("string");
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("AccountId", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("existingCustomerCountDC")
                .setDescription("Metric chart for number of existing customers")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig().setColumns(List.of(count))
                        .setName("existingCustomerCountDC").setPipelineDependencies(deps));
    }

    private static Datacard allOpenPipelineByOppType(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12) );
        return new Datacard().setName("allOpenPipelineByType")
                .setDisplayName("All Open Pipeline")
                .setDescription("All Open Pipeline ordered by type for current and next 3 quarters")
                .setContents(List.of((allOpenPipelineByType())))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization allOpenPipelineByType(){

        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Close Date").setDataType("text")
                .setColumns(List.of(
                        new QField("fiscalyear", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN)


                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("closedate").setDataType("text");

        QueryField closedate = new SimpleQField()
                .setQueryFunction(closedateNoQueryFunction)
                .setDescription("Closed Date")
                .setDisplayFormat("text");

        NoQueryFunction amtNoQueryFunction = new NoQueryFunction();
        amtNoQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("currency");

        QueryField amount = new SimpleQField()
                .setQueryFunction(amtNoQueryFunction)
                .setDescription("Opportunity Amount")
                .setDisplayFormat("currency");

        NoQueryFunction opptyTypeNoQueryFunction = new NoQueryFunction();
        opptyTypeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("Opportunity Type").setDataType("text");

        QueryField typ = new SimpleQField()
                .setQueryFunction(opptyTypeNoQueryFunction)
                .setDescription("Opportunity Type")
                .setDisplayFormat("text");

        NoQueryFunction stageNameNoQueryFunction = new NoQueryFunction();
        stageNameNoQueryFunction.setColumns(List.of(new QField("stagename", QField.Type.COLUMN)))
                .setAlias("Stage Name").setDataType("text");

        QueryField stagename = new SimpleQField()
                .setQueryFunction(stageNameNoQueryFunction)
                .setDescription("Opportunity Stage name")
                .setDisplayFormat("text");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12) );

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("AccountId", "Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("allOpenPipelineByType")
                .setDescription("All Open Pipeline ordered by type")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(quarter, amount,typ, stagename)).setName("allOpenPipelineByType")
                        .setPipelineDependencies(deps)
                        .setSortList(List.of(new Sort(new QField().setName(quarter.getAlias()), true), new Sort(new QField().setName(typ.getAlias()), true),
                                new Sort(new QField().setName(stagename.getAlias()), true)))
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range)));
    }

    private static Datacard allOpenPipelineByOppTypeDC(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12) );
        return new Datacard().setName("allOpenPipelineByTypeDC")
                .setDisplayName("All Open Pipeline")
                .setDescription("All Open Pipeline ordered by type for current and next 3 quarters")
                .setContents(List.of((allOpenPipelineByTypeNew())))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization allOpenPipelineByTypeNew(){

        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("fiscalyear", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN)


                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Close Date").setDataType("text");

        QueryField closedate = new SimpleQField()
                .setQueryFunction(closedateNoQueryFunction)
                .setDescription("Close Date")
                .setDisplayFormat("text");

        NoQueryFunction amtNoQueryFunction = new NoQueryFunction();
        amtNoQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("currency");

        QueryField amount = new SimpleQField()
                .setQueryFunction(amtNoQueryFunction)
                .setDescription("Opportunity Amount")
                .setDisplayFormat("currency");

        NoQueryFunction opptyTypeNoQueryFunction = new NoQueryFunction();
        opptyTypeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("Opportunity Type").setDataType("text");

        QueryField typ = new SimpleQField()
                .setQueryFunction(opptyTypeNoQueryFunction)
                .setDescription("Opportunity Type")
                .setDisplayFormat("text");

        NoQueryFunction stageNameNoQueryFunction = new NoQueryFunction();
        stageNameNoQueryFunction.setColumns(List.of(new QField("stagename", QField.Type.COLUMN)))
                .setAlias("Stage Name").setDataType("text");

        QueryField stagename = new SimpleQField()
                .setQueryFunction(stageNameNoQueryFunction)
                .setDescription("Opportunity Stage name")
                .setDisplayFormat("text");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("AccountId", "Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("allOpenPipelineByTypeDC")
                .setDescription("All Open Pipeline ordered by type")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(quarter,closedate, amount,typ, stagename)).setName("allOpenPipelineByTypeDC")
                        .setPipelineDependencies(deps));
    }


    private static Datacard sqlleadCountByOwner(){
        DatacardConfig config = new DatacardConfig();
        config.setDateRange(currentQuarterDateRange());

        return new Datacard().setName("sqlLeadCountByOwner")
                .setDisplayName("Qualified Leads by Owner")
                .setDescription("Leads in qualified status listed by owner")
                .setContents(List.of((sqlleadCountByOwnerTable())))
                .setConfiguration(config)
                .setSeeded(true);
    }

    private static Visualization sqlleadCountByOwnerTable(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("syncariid", QField.Type.COLUMN)))
                .setAlias("Qualified Lead Count").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total sales qualified leads for owner").setDisplayFormat("number");

        NoQueryFunction ownerNameNoQueryFunction = new NoQueryFunction();
        ownerNameNoQueryFunction.setColumns(List.of(new QField("Owner Name", QField.Type.COLUMN)))
                .setAlias("Owner Name").setDataType("text");

        QueryField ownerName = new SimpleQField()
                .setQueryFunction(ownerNameNoQueryFunction)
                .setDescription("Owner Name")
                .setDisplayFormat("text");

        NoQueryFunction createdDateNoQueryFunction = new NoQueryFunction();
        createdDateNoQueryFunction.setColumns(List.of(new QField("createddate", QField.Type.COLUMN)))
                .setAlias("createddate").setDataType("datetime");

        QueryField createdDate = new SimpleQField()
                .setQueryFunction(createdDateNoQueryFunction)
                .setDescription("Lead Created Date")
                .setDisplayFormat("datetime");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("lead", List.of("Email", "leadSource", "Status", "CreatedDate", "OwnerId")));
        deps.add(new PipelineDependency("user", List.of("Email", "Name", "Alias")));

        return new Visualization().setName("sqlLeadCountByOwner")
                .setDescription("Table for qualified lead count by owner")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(ownerName.getAlias()))))
                        .setColumns(List.of(ownerName, count)).setName("sqlLeadCountByOwner")
                        .setPipelineDependencies(deps)
                        .setDateFilter(new DateFilter().setField(createdDate).setDateRange(currentQuarterDateRange())));
    }

    private static Datacard salesFunnel(){
        return new Datacard().setName("salesFunnel")
                .setDisplayName("Sales Funnel")
                .setDescription("Current open pipeline grouped by sales stage")
                .setContents(List.of((salesFunnelByStage())))
                .setSeeded(true);
    }

    private static Visualization salesFunnelByStage(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("currency");

        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount in closed opportunity").setDisplayFormat("currency");

        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Oppty Count").setDataType("date");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total count of all open pipelines").setDisplayFormat("number");

        NoQueryFunction stageNoQueryFunction = new NoQueryFunction();
        stageNoQueryFunction.setColumns(List.of(new QField("stagename", QField.Type.COLUMN)))
                .setAlias("Stage Name").setDataType("text");

        QueryField stagename = new SimpleQField()
                .setQueryFunction(stageNoQueryFunction)
                .setDescription("Opportunity Stage name")
                .setDisplayFormat("text");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate,currentLocalDate.plusMonths(12));

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        return new Visualization().setName("salesFunnel")
                .setDescription("Sales Funnel By Stage for current and next 3 quarters")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(stagename.getAlias()))))
                        .setSortList(List.of(new Sort(new QField().setName(stagename.getAlias()), true)))
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(stagename,total,count)).setName("salesFunnel")
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range)));
    }

    private static Datacard salesFunnelDC(){
        return new Datacard().setName("salesFunnelDC")
                .setDisplayName("Sales Funnel")
                .setDescription("Current open pipeline grouped by sales stage")
                .setContents(List.of((salesFunnelByStageNew())))
                .setSeeded(true);
    }

    private static Visualization salesFunnelByStageNew(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("currency");

        QueryField total = new ComplexQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount in closed opportunity").setDisplayFormat("currency");

        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Oppty Count").setDataType("date");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total count of all open pipelines").setDisplayFormat("number");

        NoQueryFunction stageNoQueryFunction = new NoQueryFunction();
        stageNoQueryFunction.setColumns(List.of(new QField("stagename", QField.Type.COLUMN)))
                .setAlias("Stage Name").setDataType("text");

        QueryField stagename = new SimpleQField()
                .setQueryFunction(stageNoQueryFunction)
                .setDescription("Opportunity Stage name")
                .setDisplayFormat("text");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        return new Visualization().setName("salesFunnelDC")
                .setDescription("Sales Funnel By Stage for current and next 3 quarters")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setSortList(List.of(new Sort(new QField().setName(stagename.getAlias()), true)))
                        .setPipelineDependencies(deps)
                        .setColumns(List.of(stagename,total,count)).setName("salesFunnelDC"));
    }

    private static Datacard top10CustByRev(){
        return new Datacard().setName("top10CustomersByRevenue")
                .setDisplayName("Top 10 Accounts by Sales")
                .setDescription("Top 10 accounts by account name and closed won opportunity")
                .setContents(List.of((top10CustByRevnue())))
                .setSeeded(true);
    }

    private static Visualization top10CustByRevnue(){
        NoQueryFunction noQueryFunction = new NoQueryFunction();
        noQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Account Name").setDataType("text");

        QueryField accountname = new SimpleQField().setQueryFunction(noQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        NoQueryFunction accIdNoQueryFunction = new NoQueryFunction();
        accIdNoQueryFunction.setColumns(List.of(new QField().setName("accountid").setDataType("string")))
                .setAlias("accountid").setDataType("text");

        QueryField accountid = new SimpleQField().setQueryFunction(accIdNoQueryFunction).setDisplayFormat("text")
                .setDescription("account id");

        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("integer");

        QueryField amount = new SimpleQField()
                .setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount")
                .setDisplayFormat("currency");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("top10CustomersByRevenue")
                .setDescription("Top 10 customers by revenue")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(accountname,amount)).setName("top10CustomersByRevenue")
                        .setPipelineDependencies(deps)
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(accountid.getAlias())),
                                new AggregateConfig().setAggregateField(new QField().setName(accountname.getAlias()))))
                        .setSortList(List.of(new Sort(new QField().setName(amount.getAlias()), false)))
                        .setLimit(10));
    }
    private static Datacard top10CustomersByRevenueDC(){
        return new Datacard().setName("top10CustomersByRevenueDC")
                .setDisplayName("Top 10 Accounts by Sales")
                .setDescription("Top 10 accounts by account name and closed won opportunity")
                .setContents(List.of((top10CustByRevnueDC())))
                .setSeeded(true);
    }

    private static Visualization top10CustByRevnueDC(){
        NoQueryFunction noQueryFunction = new NoQueryFunction();
        noQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Account Name").setDataType("text");

        QueryField accountname = new SimpleQField().setQueryFunction(noQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        NoQueryFunction accIdNoQueryFunction = new NoQueryFunction();
        accIdNoQueryFunction.setColumns(List.of(new QField().setName("accountid").setDataType("string")))
                .setAlias("accountid").setDataType("text");

        QueryField accountid = new SimpleQField().setQueryFunction(accIdNoQueryFunction).setDisplayFormat("text")
                .setDescription("account id");

        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Amount").setDataType("integer");

        QueryField amount = new SimpleQField()
                .setQueryFunction(sumQueryFunction)
                .setDescription("Total Amount")
                .setDisplayFormat("currency");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("top10CustomersByRevenueDC")
                .setDescription("Top 10 customers by revenue")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(accountname,amount)).setName("top10CustomersByRevenueDC")
                        .setPipelineDependencies(deps)
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(accountid.getAlias())),
                                new AggregateConfig().setAggregateField(new QField().setName(accountname.getAlias()))))
                        .setSortList(List.of(new Sort(new QField().setName(amount.getAlias()), false)))
                        .setLimit(10));
    }

    private static Datacard mqlCountInCurrentQuarter(){
        DatacardConfig config = new DatacardConfig();
        config.setDateRange(currentQuarterDateRange());

        return new Datacard().setName("mqlCountInQuarter")
                .setDisplayName("Qualified Lead Count")
                .setDescription("Count of leads with qualified status")
                .setContents(List.of((mqlCountInCurrentQuarterMetric())))
                .setConfiguration(config)
                .setSeeded(true);
    }

    private static Visualization mqlCountInCurrentQuarterMetric(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("syncariid", QField.Type.COLUMN)))
                .setAlias("Marketing Qualified Lead Count").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Total marketing qualified leads").setDisplayFormat("number");

        NoQueryFunction createdDateNoQueryFunction = new NoQueryFunction();
        createdDateNoQueryFunction.setColumns(List.of(new QField("createddate", QField.Type.COLUMN)))
                .setAlias("createddate").setDataType("datetime");

        QueryField createdDate = new SimpleQField()
                .setQueryFunction(createdDateNoQueryFunction)
                .setDescription("Lead Created Date")
                .setDisplayFormat("datetime");

        NoQueryFunction statusNoQueryFunction = new NoQueryFunction();
        statusNoQueryFunction.setColumns(List.of(new QField("status", QField.Type.COLUMN)))
                .setAlias("status").setDataType("text");

        QueryField status = new SimpleQField()
                .setQueryFunction(statusNoQueryFunction)
                .setDescription("status")
                .setDisplayFormat("datetime");

        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",status.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "Qualified")
        );
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("lead", List.of("Email", "leadSource", "Status", "CreatedDate")));
        return new Visualization().setName("mqlCountInQuarter")
                .setDescription("Total marketing qualified lead count")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(count)).setName("mqlCountInQuarter")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateCondition)
                        .setDateFilter(new DateFilter().setField(createdDate).setDateRange(currentQuarterDateRange())));
    }

    private static Datacard openEscalatedTicketCount(){
        return new Datacard().setName("openEscalatedTicketCount")
                .setDisplayName("Open Escalated Ticket Count")
                .setDescription("Open Escalated Ticket Count")
                .setContents(List.of((openEscalatedTicketCountMetric())))
                .setSeeded(true);
    }

    private static Visualization openEscalatedTicketCountMetric(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setAlias("Open Escalated Tickets").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Escalated Tickets").setDisplayFormat("number");

        NoQueryFunction isClosedNoQueryFunction = new NoQueryFunction();
        isClosedNoQueryFunction.setColumns(List.of(new QField("isClosed", QField.Type.COLUMN)))
                .setAlias("isClosed").setDataType("boolean");

        NoQueryFunction isEscalatedNoQueryFunction = new NoQueryFunction();
        isEscalatedNoQueryFunction.setColumns(List.of(new QField("isEscalated", QField.Type.COLUMN)))
                .setAlias("isEscalated").setDataType("boolean");

        QueryField isClosed = new SimpleQField()
                .setQueryFunction(isClosedNoQueryFunction)
                .setDescription("Is Closed")
                .setDisplayFormat("boolean");

        QueryField isEscalated = new SimpleQField()
                .setQueryFunction(isEscalatedNoQueryFunction)
                .setDescription("Is Escalated")
                .setDisplayFormat("boolean");

        Map<String, Object> pred1 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",isClosed.getAlias()),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", true)
        );

        Map<String, Object> pred2 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",isEscalated.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", true)
        );
        Map<String, Object> predicateMap = new HashMap<>();
        predicateMap.putAll(Map.of("predicates", List.of(pred1, pred2),"operator", "AND"));

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        return new Visualization().setName("openEscalatedTicketCount")
                .setDescription("Open Escalated Ticket count")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(count)).setName("openEscalatedTicketCount")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateMap));
    }

    private static Datacard openTicketsCountByAccount(){
        return new Datacard().setName("openTicketsCountByAccount")
                .setDisplayName("Accounts by Open Ticket Count")
                .setDescription("Accounts by Open Ticket Count")
                .setContents(List.of((openTicketsCountByAccountTable())))
                .setSeeded(true);
    }

    private static Visualization openTicketsCountByAccountTable(){
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setAlias("Open Tickets").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Tickets").setDisplayFormat("number");

        NoQueryFunction isClosedNoQueryFunction = new NoQueryFunction();
        isClosedNoQueryFunction.setColumns(List.of(new QField("isClosed", QField.Type.COLUMN)))
                .setAlias("isClosed").setDataType("boolean");

        NoQueryFunction accNameNoQueryFunction = new NoQueryFunction();
        accNameNoQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Account Name").setDataType("text");
        QueryField accName = new SimpleQField().setQueryFunction(accNameNoQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        QueryField isClosed = new SimpleQField()
                .setQueryFunction(isClosedNoQueryFunction)
                .setDescription("Is Closed")
                .setDisplayFormat("boolean");

        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",isClosed.getAlias()),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", true)
        );

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at", "AccountId")));
        deps.add(new PipelineDependency("account", List.of("Name")));

        return new Visualization().setName("openTicketsCountByAccount")
                .setDescription("Open Ticket count by account")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName("name"))))
                        .setColumns(List.of(accName, count)).setName("openTicketsCountByAccount")
                        .setPipelineDependencies(deps)
                        .setSortList(List.of(new Sort(new QField().setName(count.getAlias()), false)))
                        .setPredicate(predicateCondition));
    }
    private static Datacard openTicketsCountByAccountDC(){
        return new Datacard().setName("openTicketsCountByAccount")
                .setDisplayName("Accounts by Open Ticket Count")
                .setDescription("Accounts by Open Ticket Count")
                .setContents(List.of((openTicketsCountByAccountTableDC())))
                .setSeeded(true);
    }

    private static Visualization openTicketsCountByAccountTableDC(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();
        distinctQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setAlias("Open Tickets").setDataType("integer");
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Tickets").setDisplayFormat("number");

        NoQueryFunction isClosedNoQueryFunction = new NoQueryFunction();
        isClosedNoQueryFunction.setColumns(List.of(new QField("isClosed", QField.Type.COLUMN)))
                .setAlias("isClosed").setDataType("boolean");

        NoQueryFunction accNameNoQueryFunction = new NoQueryFunction();
        accNameNoQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Account Name").setDataType("text");
        QueryField accName = new SimpleQField().setQueryFunction(accNameNoQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        QueryField isClosed = new SimpleQField()
                .setQueryFunction(isClosedNoQueryFunction)
                .setDescription("Is Closed")
                .setDisplayFormat("boolean");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at", "AccountId")));
        deps.add(new PipelineDependency("account", List.of("Name")));

        return new Visualization().setName("openTicketsCountByAccountDC")
                .setDescription("Open Ticket count by account")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(accName, count)).setName("openTicketsCountByAccountDC")
                        .setPipelineDependencies(deps));
    }

    private static Datacard openTicketWithOpenPipeline(){
        return new Datacard().setName("openTicketsInAccountsWithOpenPipeline")
                .setDisplayName("Open Tickets in Accounts with Open Pipeline")
                .setDescription("Number of Open Tickets with Open Pipeline Accounts")
                .setContents(List.of((openTicketsInAccountsWithOpenPipeline())))
                .setSeeded(true);
    }

    private static Visualization openTicketsInAccountsWithOpenPipeline(){
        NoQueryFunction noQueryFunction = new NoQueryFunction();
        noQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Account Name").setDataType("text");

        QueryField accountname = new SimpleQField().setQueryFunction(noQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("Ticket Count").setDataType("integer")
                .setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)));

        QueryField caseCount = new SimpleQField().setQueryFunction(countQueryFunction).setDisplayFormat("number")
                .setDescription("Case count for open pipeline accounts");

        NoQueryFunction accountNoQueryFunction = new NoQueryFunction();
        accountNoQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setAlias("accountid").setDataType("text");

        QueryField accountId = new SimpleQField()
                .setQueryFunction(accountNoQueryFunction)
                .setDescription("Account Id")
                .setDisplayFormat("text");


        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Open Pipeline Amount").setDataType("integer");

        QueryField amount = new SimpleQField()
                .setQueryFunction(sumQueryFunction)
                .setDescription("Open Pipeline Amount")
                .setDisplayFormat("currency");


        NoQueryFunction statusNoQueryFunction = new NoQueryFunction();
        statusNoQueryFunction.setColumns(List.of(new QField("status", QField.Type.COLUMN)))
                .setAlias("status").setDataType("text");
        QueryField status = new SimpleQField().setQueryFunction(statusNoQueryFunction)
                .setDescription("Ticket Status").setDisplayFormat("text");

        Map<String, Object> predicateCondition = new HashMap<>();
        predicateCondition.put("left", Map.of("datatype", "string", "type", "variable", "value",accountId.getAlias()));
        predicateCondition.put("operator", "in");
        predicateCondition.put("right", Map.of("type", "vizconfig"));

        Map<String, Object> pred2 = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",status.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "open")
        );
        Map<String, Object> predicateMap = new HashMap<>();
        predicateMap.putAll(Map.of("predicates", List.of(predicateCondition, pred2),"operator", "AND"));

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at", "AccountId")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        return new Visualization().setName("openTicketsInAccountsWithOpenPipeline")
                .setDescription("Open tickets of open pipeline accounts")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(accountname, caseCount)).setName("openTicketsInAccountsWithOpenPipeline")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateMap)
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(accountname.getAlias()))))
                        .setChildVizConfig(new VizConfig().setName("openTicketsInAccountsWithOpenPipelineChild").setColumns(List.of(accountId))));
    }

    private static Datacard openTicketsAccountforOpenPipeline(){
        return new Datacard().setName("openTicketsAccountforOpenPipeline")
                .setDisplayName("Open Tickets with Open Pipeline")
                .setDescription("Number of Open Tickets with Open Pipeline Accounts")
                .setContents(List.of((openTicketsAccountforOpenPipelineImpl())))
                .setSeeded(true);
    }

    private static Visualization openTicketsAccountforOpenPipelineImpl(){
        NoQueryFunction noQueryFunction = new NoQueryFunction();
        noQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Account Name").setDataType("text");

        QueryField accountname = new SimpleQField().setQueryFunction(noQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        CountQueryFunction countQueryFunction = new CountQueryFunction();
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setDataType("number");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setColumns(List.of(new QField().setName("casenumber").setDataType("string")))
                .setAlias("Ticket Count").setDataType("number");

        QueryField caseCount = new SimpleQField().setQueryFunction(countQueryFunction).setDisplayFormat("text")
                .setDescription("Case count for open pipeline accounts");


        CountQueryFunction countQueryFunctionForDivision = new CountQueryFunction();
        countQueryFunctionForDivision.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunctionForDivision.setColumns(List.of(new QField().setName("casenumber").setDataType("string")))
                .setDataType("number");

        QueryField caseCountForDivision = new SimpleQField().setQueryFunction(countQueryFunctionForDivision).setDisplayFormat("text");

        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setDataType("integer");

        QueryField amount = new SimpleQField()
                .setQueryFunction(sumQueryFunction)
                .setDescription("Open Pipeline Amount")
                .setDisplayFormat("number");


        DivideQueryFunction divQueryFunction = new DivideQueryFunction();
        divQueryFunction.setInnerQueryFields(List.of(amount, caseCountForDivision))
                .setAlias("Open Pipeline Amount").setDataType("number");
        QueryField openPipelineAmount = new ComplexQField().setQueryFunction(divQueryFunction).setDisplayFormat("currency")
                .setDescription("Open Pipeline Amount");


        NoQueryFunction isClosedTicketNoQueryFunction = new NoQueryFunction();
        isClosedTicketNoQueryFunction.setColumns(List.of(new QField("ticketsisclosed", QField.Type.COLUMN)))
                .setAlias("isClosedTicket").setDataType("text");
        QueryField status = new SimpleQField().setQueryFunction(isClosedTicketNoQueryFunction)
                .setDescription("Ticket Is Closed").setDisplayFormat("text");

        NoQueryFunction isClosedOpptyNoQueryFunction = new NoQueryFunction();
        isClosedOpptyNoQueryFunction.setColumns(List.of(new QField("opptyisclosed", QField.Type.COLUMN)))
                .setAlias("isOpptyClosed").setDataType("text");
        QueryField opptyStatus = new SimpleQField().setQueryFunction(isClosedOpptyNoQueryFunction)
                .setDescription("Oppty Status Is Closed").setDisplayFormat("text");


        Map<String, Object> pred2 = Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value",status.getName()),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", true)
        );
        Map<String, Object> pred1 = Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value",accountname.getName()),
                "operator", "not_empty"
        );

        Map<String, Object> pred3 = Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value",amount.getName()),
                "operator", "not_empty"
        );

        Map<String, Object> pred4 = Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value","casenumber"),
                "operator", "not_empty"
        );

        Map<String, Object> pred5 = Map.of(
                "left", Map.of("datatype", "boolean", "type", "variable", "value",opptyStatus.getName()),
                "operator", "ne",
                "right", Map.of("type", "literal", "value", true)
        );

        Map<String, Object> predicateMap = new HashMap<>();
        predicateMap.putAll(Map.of("predicates", List.of(pred1, pred2, pred3, pred4, pred5),"operator", "AND"));
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at", "AccountId")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("openTicketsAccountforOpenPipeline")
                .setDescription("Open tickets of open pipeline accounts")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(accountname, caseCount,openPipelineAmount)).setName("openTicketsAccountforOpenPipeline")
                        .setPredicate(predicateMap)
                        .setPipelineDependencies(deps)
                        .setSortList(List.of(new Sort(new QField().setName(openPipelineAmount.getAlias()),false)))
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(accountname.getAlias())))));
    }

    private static Datacard openTicketsAccountforOpenPipelineDC(){
        return new Datacard().setName("openTicketsAccountforOpenPipelineDC")
                .setDisplayName("Open Tickets with Open Pipeline")
                .setDescription("Number of Open Tickets with Open Pipeline Accounts")
                .setContents(List.of((openTicketsAccountforOpenPipelineDCImpl())))
                .setSeeded(true);
    }

    private static Visualization openTicketsAccountforOpenPipelineDCImpl(){
        NoQueryFunction noQueryFunction = new NoQueryFunction();
        noQueryFunction.setColumns(List.of(new QField("name", QField.Type.ENTITY)))
                .setAlias("Account Name").setDataType("text");

        QueryField accountname = new SimpleQField().setQueryFunction(noQueryFunction)
                .setDescription("Account Name").setDisplayFormat("text");

        CountQueryFunction countQueryFunction = new CountQueryFunction();
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                .setDataType("number");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setColumns(List.of(new QField().setName("casenumber").setDataType("string")))
                .setAlias("Ticket Count").setDataType("number");

        QueryField caseCount = new SimpleQField().setQueryFunction(countQueryFunction).setDisplayFormat("text")
                .setDescription("Case count for open pipeline accounts");

        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setDataType("integer");

        QueryField amount = new SimpleQField()
                .setQueryFunction(sumQueryFunction)
                .setDescription("Open Pipeline Amount")
                .setDisplayFormat("number");

        DivideQueryFunction divQueryFunction = new DivideQueryFunction();
        divQueryFunction.setInnerQueryFields(List.of(amount, caseCount))
                .setAlias("Open Pipeline Amount").setDataType("number");
        QueryField openPipelineAmount = new ComplexQField().setQueryFunction(divQueryFunction).setDisplayFormat("currency")
                .setDescription("Open Pipeline Amount");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at", "AccountId")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));

        return new Visualization().setName("openTicketsAccountforOpenPipelineDC")
                .setDescription("Open tickets of open pipeline accounts")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(accountname, caseCount,openPipelineAmount)).setName("openTicketsAccountforOpenPipelineDC")
                        .setPipelineDependencies(deps));
    }


    private static DateRange currentQuarterDateRange(){
        int currentQuarter = LocalDateTime.now().get(IsoFields.QUARTER_OF_YEAR);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        LocalDateTime start = LocalDateTime.of(currentYear, 3 * (currentQuarter - 1) + 1, 1, 0, 0, 0, 1);

        LocalDateTime endMonth = start.withMonth(currentQuarter * 3);
        LocalDateTime endMonthDay = endMonth.withDayOfMonth(
                endMonth.getMonth().length(endMonth.toLocalDate().isLeapYear()));
        LocalDateTime end = LocalDateTime.of(currentYear, endMonth.getMonthValue(), endMonthDay.getDayOfMonth(), 23, 59, 59, 999999999);
        return new DateRange(start, end);
    }


    private static Datacard trendClosedTicket24Hours(){
        LocalDateTime currentDate = LocalDateTime.now();
        DateRange range = new DateRange(currentDate.minusDays(1),currentDate);
        return new Datacard().setName("trendOfIssuesResolvedIn24hours")
                .setDisplayName("Closed Ticket Trend 24 Hours")
                .setDescription("Trend of Closed Ticket in 24 Hours")
                .setContents(List.of((trendOfIssuesResolvedIn24hours())))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization trendOfIssuesResolvedIn24hours(){
        DateTruncQueryFunction dateTruncQueryFunction = new DateTruncQueryFunction();
        dateTruncQueryFunction.setColumns(List.of(new QField("updated_at", QField.Type.COLUMN)))
                .setAlias("Closed Date").setDataType("text");
        dateTruncQueryFunction.setTruncatedField("hour");

        QueryField dateHourField = new ComplexQField().setQueryFunction(dateTruncQueryFunction)
                .setDescription("Closed Date Hour").setDisplayFormat("text");

        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("status", QField.Type.COLUMN)))
                .setAlias("Count").setDataType("integer");
        QueryField countField = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("count").setDisplayFormat("number");

        NoQueryFunction createdDateNoQueryFunction = new NoQueryFunction();
        createdDateNoQueryFunction.setColumns(List.of(new QField("createddate", QField.Type.COLUMN)))
                .setAlias("createddate").setDataType("text");
        QueryField createddate = new SimpleQField().setQueryFunction(createdDateNoQueryFunction)
                .setDescription("Created Date").setDisplayFormat("text");

        NoQueryFunction statusNoQueryFunction = new NoQueryFunction();
        statusNoQueryFunction.setColumns(List.of(new QField("status", QField.Type.COLUMN)))
                .setAlias("status").setDataType("text");
        QueryField status = new SimpleQField().setQueryFunction(statusNoQueryFunction)
                .setDescription("Ticket Status").setDisplayFormat("text");

        LocalDateTime currentDate = LocalDateTime.now();
        DateRange range = new DateRange(currentDate.minusDays(1),currentDate);
        Map<String, Object> predicateMap = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",status.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "closed")
        );

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        return new Visualization().setName("trendOfIssuesResolvedIn24hours")
                .setDescription("Closed Ticket Trend in 24 hours")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(dateHourField).setYAxis(List.of(countField))
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(dateHourField.getAlias()))))
                        .setColumns(List.of(dateHourField, countField)).setName("trendOfIssuesResolvedIn24hours")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateMap)
                        .setDateFilter(new DateFilter().setField(createddate).setDateRange(range)));
    }

    private static Datacard trendClosedTicket7days(){
        return new Datacard().setName("trendOfIssuesResolvedIn7Days")
                .setDisplayName("Closed Ticket Trend 7 days")
                .setDescription("Trend of Closed Ticket in 7 days\"")
                .setContents(List.of((trendOfIssuesResolvedInLast7days())))
                .setSeeded(true);
    }

    private static Visualization trendOfIssuesResolvedInLast7days(){
        DateTruncQueryFunction dateTruncQueryFunction = new DateTruncQueryFunction();
        dateTruncQueryFunction.setColumns(List.of(new QField("updated_at", QField.Type.COLUMN)))
                .setAlias("Closed Date").setDataType("text");
        dateTruncQueryFunction.setTruncatedField("day");

        QueryField dateDayField = new ComplexQField().setQueryFunction(dateTruncQueryFunction)
                .setDescription("Closed Date Days").setDisplayFormat("text");

        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setColumns(List.of(new QField("status", QField.Type.COLUMN)))
                .setAlias("Count").setDataType("integer");
        QueryField countField = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("count").setDisplayFormat("number");

        NoQueryFunction createdDateNoQueryFunction = new NoQueryFunction();
        createdDateNoQueryFunction.setColumns(List.of(new QField("createddate", QField.Type.COLUMN)))
                .setAlias("createddate").setDataType("text");
        QueryField createddate = new SimpleQField().setQueryFunction(createdDateNoQueryFunction)
                .setDescription("Created Date").setDisplayFormat("text");

        NoQueryFunction statusNoQueryFunction = new NoQueryFunction();
        statusNoQueryFunction.setColumns(List.of(new QField("status", QField.Type.COLUMN)))
                .setAlias("status").setDataType("text");
        QueryField status = new SimpleQField().setQueryFunction(statusNoQueryFunction)
                .setDescription("Ticket Status").setDisplayFormat("text");

        LocalDateTime currentDate = LocalDateTime.now();
        DateRange range = new DateRange(currentDate.minusDays(7),currentDate);

        Map<String, Object> predicateMap = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",status.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "closed")
        );
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        return new Visualization().setName("trendOfIssuesResolvedIn7Days")
                .setDescription("Closed Ticket Trend in last 7 days")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(dateDayField).setYAxis(List.of(countField))
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName(dateDayField.getAlias()))))
                        .setColumns(List.of(dateDayField, countField)).setName("trendOfIssuesResolvedIn7Days")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateMap)
                        .setDateFilter(new DateFilter().setField(createddate).setDateRange(range)));
    }

    private static Datacard openRenewalLogoCountSeed(){
        return new Datacard().setName("openRenewalLogoCount")
                .setDisplayName("Renewal Logo Count")
                .setDescription("Customers with upcoming renewal opportunities")
                .setContents(List.of(openRenewalLogoCount()))
                .setConfiguration(new DatacardConfig().setDateRange(getCurrentYearDateRange()))
                .setSeeded(true);
    }
    private static Visualization openRenewalLogoCount(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("Open Renewal Logo Count").setDataType("integer")
                .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Renewal Logo Count").setDisplayFormat("number");

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",type.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "Renewal")
        );

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");


        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("openRenewalLogoCount")
                .setDescription("Open Renewal Logo Count")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(count))
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateCondition)
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(getCurrentYearDateRange()))
                        .setName("openRenewalLogoCount"));
    }

    private static Datacard openRenewalLogoCountDC(){
        return new Datacard().setName("openRenewalLogoCountDC")
                .setDisplayName("Renewal Logo Count")
                .setDescription("Customers with upcoming renewal opportunities")
                .setContents(List.of(openRenewalLogoCountNew()))
                .setSeeded(true);
    }
    private static Visualization openRenewalLogoCountNew(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("Open Renewal Logo Count").setDataType("integer")
                .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Renewal Logo Count").setDisplayFormat("number");

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("openRenewalLogoCountDC")
                .setDescription("Open Renewal Logo Count")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(count))
                        .setPipelineDependencies(deps)
                        .setName("openRenewalLogoCountDC"));
    }

    private static Datacard openRenewalsSeed(){
        return new Datacard().setName("openRenewals")
                .setDisplayName("Open Renewals")
                .setDescription("Open renewal opportunities")
                .setContents(List.of((openRenewalsBar())))
                .setConfiguration(new DatacardConfig().setDateRange(getCurrentYearDateRange()))
                .setSeeded(true);
    }

    private static Visualization openRenewalsBar() {

        DateTruncQueryFunction dateTruncQueryFunction = new DateTruncQueryFunction();
        dateTruncQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Close Date Month").setDataType("text");
        dateTruncQueryFunction.setTruncatedField("month");
        QueryField dateMonthField = new ComplexQField().setQueryFunction(dateTruncQueryFunction)
                .setDescription("Closed Date Month").setDisplayFormat("text");

        ToCharQueryFunction toCharQueryFunction = new ToCharQueryFunction();
        toCharQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Close Month").setDataType("text");
        toCharQueryFunction.setToCharField("Mon");
        QueryField monthField = new ComplexQField().setQueryFunction(toCharQueryFunction)
                .setDescription("Close Month").setDisplayFormat("text");

        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("Open Renewal Logo Count").setDataType("integer")
                .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Renewals").setDisplayFormat("number");

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",type.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "Renewal")
        );

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("Close Date").setDataType("date");

        QueryField closeDateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Close Date");


        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("openRenewals")
                .setDescription("Bar chart for Open Renewals")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(monthField).setYAxis(List.of(count))
                        .setGroupingColumns(List.of(
                                new AggregateConfig().setAggregateField(new QField().setName(dateMonthField.getAlias())),
                                new AggregateConfig().setAggregateField(new QField().setName(monthField.getAlias()))))
                        .setSortList(List.of(new Sort(new QField().setName(dateMonthField.getAlias()), true)))
                        .setDateFilter(new DateFilter().setField(closeDateField).setDateRange(getCurrentYearDateRange()))
                        .setColumns(List.of(count, dateMonthField, monthField)).setName("openRenewals")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateCondition));

    }
    private static Datacard openRenewalsSeedDC(){
        return new Datacard().setName("openRenewalsDC")
                .setDisplayName("Open Renewals")
                .setDescription("Open renewal opportunities")
                .setContents(List.of((openRenewalsBarDC())))
                .setSeeded(true);
    }

    private static Visualization openRenewalsBarDC() {

        DateTruncQueryFunction dateTruncQueryFunction = new DateTruncQueryFunction();
        dateTruncQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Close Date Month").setDataType("text");
        dateTruncQueryFunction.setTruncatedField("month");
        QueryField dateMonthField = new ComplexQField().setQueryFunction(dateTruncQueryFunction)
                .setDescription("Closed Date Month").setDisplayFormat("text");

        ToCharQueryFunction toCharQueryFunction = new ToCharQueryFunction();
        toCharQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                .setAlias("Close Month").setDataType("text");
        toCharQueryFunction.setToCharField("Mon");
        QueryField monthField = new ComplexQField().setQueryFunction(toCharQueryFunction)
                .setDescription("Close Month").setDisplayFormat("text");

        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setAlias("Open Renewal Logo Count").setDataType("integer")
                .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("Open Renewals").setDisplayFormat("number");

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("openRenewalsDC")
                .setDescription("Bar chart for Open Renewals")
                .setType(VizType.COLUMN)
                .setConfig(new BarVizConfig().setXAxis(monthField).setYAxis(List.of(count))
                        .setColumns(List.of(count, dateMonthField, monthField)).setName("openRenewalsDC")
                        .setPipelineDependencies(deps));

    }

    private static Datacard upcomingRenewalSeed(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange dateRange = new DateRange(currentLocalDate,currentLocalDate.plusMonths(6));

        return new Datacard().setName("upcomingRenewalDates")
                .setDisplayName("Upcoming Renewal Dates")
                .setDescription("Upcoming renewal opportunity dates")
                .setContents(List.of(upcomingRenewal(dateRange)))
                .setConfiguration(new DatacardConfig().setDateRange(dateRange))
                .setSeeded(true);
    }
    private static Visualization upcomingRenewal(DateRange range){
        NoQueryFunction nameNoQueryFunction = new NoQueryFunction();
        nameNoQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Opportunity Name").setDataType("text");
        QueryField opptyName = new SimpleQField()
                .setQueryFunction(nameNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        NoQueryFunction amountNoQueryFunction = new NoQueryFunction();
        amountNoQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Open Pipeline Amount").setDataType("integer");
        QueryField amount = new SimpleQField()
                .setQueryFunction(amountNoQueryFunction)
                .setDescription("Opportunity Amount")
                .setDisplayFormat("currency");

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",type.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "Renewal")
        );

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("Close Date").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Close Date");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("upcomingRenewalDates")
                .setDescription("Upcoming Renewals")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(amount, opptyName, dateField))
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateCondition)
                        .setSortList(List.of(new Sort(new QField().setName(amount.getAlias()), false)))
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range))
                        .setName("upcomingRenewalDates"));
    }
    private static Datacard upcomingRenewalSeedDC(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange dateRange = new DateRange(currentLocalDate,currentLocalDate.plusMonths(6));

        return new Datacard().setName("upcomingRenewalDatesDC")
                .setDisplayName("Upcoming Renewal Dates")
                .setDescription("Upcoming renewal opportunity dates")
                .setContents(List.of(upcomingRenewalDC()))
                .setConfiguration(new DatacardConfig().setDateRange(dateRange))
                .setSeeded(true);
    }
    private static Visualization upcomingRenewalDC(){
        NoQueryFunction nameNoQueryFunction = new NoQueryFunction();
        nameNoQueryFunction.setColumns(List.of(new QField("name", QField.Type.COLUMN)))
                .setAlias("Opportunity Name").setDataType("text");
        QueryField opptyName = new SimpleQField()
                .setQueryFunction(nameNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        NoQueryFunction amountNoQueryFunction = new NoQueryFunction();
        amountNoQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Open Pipeline Amount").setDataType("integer");
        QueryField amount = new SimpleQField()
                .setQueryFunction(amountNoQueryFunction)
                .setDescription("Opportunity Amount")
                .setDisplayFormat("currency");

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("Close Date").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Close Date");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("upcomingRenewalDatesDC")
                .setDescription("Upcoming Renewals")
                .setType(VizType.TABLE)
                .setConfig(new TableVizConfig()
                        .setColumns(List.of(amount, opptyName, dateField))
                        .setPipelineDependencies(deps)
                        .setName("upcomingRenewalDatesDC"));
    }



    private static Datacard revenueChurnByQuarter(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate.minusMonths(12),currentLocalDate);
        return new Datacard().setName("revenueChurnByQuarter")
                .setDisplayName("Revenue Churn By Quarter")
                .setDescription("Churn amount in closed lost opportunities from previously won accounts")
                .setContents(List.of((revenueChurnByQuarterImpl())))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization revenueChurnByQuarterImpl(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Revenue").setDataType("integer");
        QueryField total = new SimpleQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total lost revenue for churned customer").setDisplayFormat("currency");


        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate.minusMonths(12),currentLocalDate);

        NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
        typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                .setAlias("type").setDataType("text");
        QueryField type = new SimpleQField()
                .setQueryFunction(typeNoQueryFunction)
                .setDescription("type")
                .setDisplayFormat("text");

        Map<String, Object> predicateCondition = Map.of(
                "left", Map.of("datatype", "string", "type", "variable", "value",type.getAlias()),
                "operator", "eq",
                "right", Map.of("type", "literal", "value", "Renewal")
        );



        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));

        return new Visualization().setName("revenueChurnByQuarter")
                .setDescription("Line chart for Quarterly Churned Revenue By Customer")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        .setGroupingColumns(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))))
                        .setSortList(List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true)))
                        .setColumns(List.of(total, quarter)).setName("revenueChurnByQuarter")
                        .setPipelineDependencies(deps)
                        .setPredicate(predicateCondition)
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range)));

    }

    private static Datacard revenueChurnByQuarterDC(){
        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate.minusMonths(12),currentLocalDate);
        return new Datacard().setName("revenueChurnByQuarterDC")
                .setDisplayName("Revenue Churn By Quarter")
                .setDescription("Churn amount in closed lost opportunities from previously won accounts")
                .setContents(List.of((revenueChurnByQuarterImplDC())))
                .setConfiguration(new DatacardConfig().setDateRange(range))
                .setSeeded(true);
    }

    private static Visualization revenueChurnByQuarterImplDC(){
        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setAlias("Revenue").setDataType("integer");
        QueryField total = new SimpleQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total lost revenue for churned customer").setDisplayFormat("currency");


        ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
        concatQueryFunction.setAlias("Quarter").setDataType("text")
                .setColumns(List.of(
                        new QField("Q", QField.Type.LITERAL),
                        new QField("fiscalquarter", QField.Type.COLUMN),
                        new QField(" ", QField.Type.LITERAL),
                        new QField("fiscalyear", QField.Type.COLUMN)
                ));
        QueryField quarter = new ComplexQField().setQueryFunction(concatQueryFunction).setDisplayFormat("text")
                .setDescription("Quarter");

        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                .setAlias("closedate").setDataType("date");

        QueryField dateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Date");

        LocalDateTime currentLocalDate = getCurrentQuarterFirsDate();
        DateRange range = new DateRange(currentLocalDate.minusMonths(12),currentLocalDate);


        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));

        return new Visualization().setName("revenueChurnByQuarterDC")
                .setDescription("Line chart for Quarterly Churned Revenue By Customer")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(quarter).setYAxis(List.of(total))
                        .setColumns(List.of(total, quarter)).setName("revenueChurnByQuarterDC")
                        .setPipelineDependencies(deps)
                        .setDateFilter(new DateFilter().setField(dateField).setDateRange(range)));

    }

    private static Datacard userGrowthSeed(){
        return new Datacard().setName("userGrowth")
                .setDisplayName("User Growth")
                .setDescription("Monthly User Growth")
                .setContents(List.of((userGrowthLineChart())))
                .setConfiguration(new DatacardConfig().setDateRange(getCurrentYearDateRange()))
                .setSeeded(true);
    }

    private static Visualization userGrowthLineChart() {

        DateTruncQueryFunction dateTruncQueryFunction = new DateTruncQueryFunction();
        dateTruncQueryFunction.setColumns(List.of(new QField("createddate", QField.Type.COLUMN)))
                .setAlias("Created Date Month").setDataType("text");
        dateTruncQueryFunction.setTruncatedField("month");
        QueryField dateMonthField = new ComplexQField().setQueryFunction(dateTruncQueryFunction)
                .setDescription("Created Date Month").setDisplayFormat("text");

        ToCharQueryFunction toCharQueryFunction = new ToCharQueryFunction();
        toCharQueryFunction.setColumns(List.of(new QField("createddate", QField.Type.COLUMN)))
                .setAlias("Month").setDataType("text");
        toCharQueryFunction.setToCharField("Mon");
        QueryField monthField = new ComplexQField().setQueryFunction(toCharQueryFunction)
                .setDescription("Created Month").setDisplayFormat("text");


        CountQueryFunction countQueryFunction = new CountQueryFunction();
        countQueryFunction.setAlias("User Count").setDataType("integer")
                .setColumns(List.of(new QField("syncariid", QField.Type.COLUMN)));
        QueryField count = new ComplexQField().setQueryFunction(countQueryFunction)
                .setDescription("User Count").setDisplayFormat("number");


        NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
        closedateNoQueryFunction.setColumns(List.of(new QField().setName("createddate").setDataType("date")))
                .setAlias("Created Date").setDataType("date");

        QueryField createdDateField = new SimpleQField().setQueryFunction(closedateNoQueryFunction).setDisplayFormat("text")
                .setDescription("Created Date");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("user", List.of("Email", "FirstName", "LastName", "CreatedDate")));
        return new Visualization().setName("userGrowth")
                .setDescription("Monthly User Growth")
                .setType(VizType.LINE)
                .setConfig(new LineVizConfig().setXAxis(monthField).setYAxis(List.of(count))
                        .setPipelineDependencies(deps)
                        .setName("userGrowth")
                        .setColumns(List.of(dateMonthField, monthField, count))
                        .setGroupingColumns(List.of(
                                new AggregateConfig().setAggregateField(new QField().setName(dateMonthField.getAlias())),
                                new AggregateConfig().setAggregateField(new QField().setName(monthField.getAlias()))))
                        .setSortList(List.of(new Sort(new QField().setName(dateMonthField.getAlias()), true)))
                        .setDateFilter(new DateFilter().setField(createdDateField).setDateRange(getCurrentYearDateRange())));
    }

    private static Datacard avgRevenueForAllAccounts(){
        return new Datacard().setName("avgRevenueForAllAccounts")
                .setDisplayName("Average sales per account")
                .setDescription("Divides the total amount of closed won opportunity amounts by the number of unique accounts")
                .setContents(List.of((avgRevenueForAllAccountsImpl())))
                .setSeeded(true);
    }

    private static Visualization avgRevenueForAllAccountsImpl(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("integer");
        QueryField totalCount = new SimpleQField().setQueryFunction(countQueryFunction)
                .setDescription("Total count of won accounts").setDisplayFormat("number");


        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setDataType("integer");
        QueryField total = new SimpleQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total lost revenue for churned customer").setDisplayFormat("currency");

        DivideQueryFunction divQueryFunction = new DivideQueryFunction();
        divQueryFunction.setInnerQueryFields(List.of(total, totalCount))
                .setAlias("Average sales per Account").setDataType("number");
        QueryField average = new ComplexQField().setQueryFunction(divQueryFunction).setDisplayFormat("currency")
                .setDescription("Divides the total amount of closed won opportunity amounts by the number of unique accounts");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("avgRevenueForAllAccounts")
                .setDescription("Metric chart for division of the total amount of closed won opportunity amounts by the number of unique accounts")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(average))
                        .setPipelineDependencies(deps)
                        .setName("avgRevenueForAllAccounts"));
    }
    private static Datacard avgRevenueForAllAccountsDC(){
        return new Datacard().setName("avgRevenueForAllAccountsDC")
                .setDisplayName("Average sales per account")
                .setDescription("Divides the total amount of closed won opportunity amounts by the number of unique accounts")
                .setContents(List.of((avgRevenueForAllAccountsImplDC())))
                .setSeeded(true);
    }

    private static Visualization avgRevenueForAllAccountsImplDC(){
        DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
        CountQueryFunction countQueryFunction = new CountQueryFunction();

        distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("text");
        countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
        countQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                .setDataType("integer");
        QueryField totalCount = new SimpleQField().setQueryFunction(countQueryFunction)
                .setDescription("Total count of won accounts").setDisplayFormat("number");


        SumQueryFunction sumQueryFunction = new SumQueryFunction();
        sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                .setDataType("integer");
        QueryField total = new SimpleQField().setQueryFunction(sumQueryFunction)
                .setDescription("Total lost revenue for churned customer").setDisplayFormat("currency");

        DivideQueryFunction divQueryFunction = new DivideQueryFunction();
        divQueryFunction.setInnerQueryFields(List.of(total, totalCount))
                .setAlias("Average sales per Account").setDataType("number");
        QueryField average = new ComplexQField().setQueryFunction(divQueryFunction).setDisplayFormat("currency")
                .setDescription("Divides the total amount of closed won opportunity amounts by the number of unique accounts");

        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("opportunity", List.of("Amount", "FiscalQuarter", "FiscalYear", "CloseDate", "IsClosed", "IsWon", "StageName", "Type")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        return new Visualization().setName("avgRevenueForAllAccountsDC")
                .setDescription("Metric chart for division of the total amount of closed won opportunity amounts by the number of unique accounts")
                .setType(VizType.METRIC)
                .setConfig(new MetricVizConfig()
                        .setColumns(List.of(average))
                        .setPipelineDependencies(deps)
                        .setName("avgRevenueForAllAccountsDC"));
    }

    private static Datacard trendOfIssuesResolvedIn24HoursDC() {
        LineVizConfig vizConfig = new LineVizConfig();
        vizConfig.setName("trendOfIssuesResolvedIn24HoursDC");
        SimpleQField dateField = new SimpleQField();
        dateField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Closed Hour").setType(QField.Type.DATASET)))
                .setAlias("Closed Hour");
        dateField.setDisplayFormat("string");

        SimpleQField ticketCountField = new SimpleQField();
        ticketCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Ticket Count").setType(QField.Type.DATASET)))
                .setAlias("Ticket Count");
        ticketCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(ticketCountField,dateField));
        vizConfig.setXAxis(dateField);
        vizConfig.setYAxis(List.of(ticketCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("trendOfIssuesResolvedIn24HoursDC").setConfig(vizConfig)
                .setType(VizType.LINE).setDisplayName("Closed Ticket Trend 7 days");
        Datacard datacard = new Datacard().setName("trendOfIssuesResolvedIn24HoursDC")
                .setDisplayName("Closed Ticket Trend in 24 hours")
                .setDescription("Trend of Closed Ticket in 24 Hours")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard trendOfIssuesResolvedIn7DaysDC() {
        LineVizConfig vizConfig = new LineVizConfig();
        vizConfig.setName("trendOfIssuesResolvedIn7DaysDC");
        SimpleQField dateField = new SimpleQField();
        dateField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Closed Date").setType(QField.Type.DATASET)))
                .setAlias("Closed Date");
        dateField.setDisplayFormat("string");

        SimpleQField ticketCountField = new SimpleQField();
        ticketCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Ticket Count").setType(QField.Type.DATASET)))
                .setAlias("Ticket Count");
        ticketCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(ticketCountField,dateField));
        vizConfig.setXAxis(dateField);
        vizConfig.setYAxis(List.of(ticketCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("trendOfIssuesResolvedIn7DaysDC").setConfig(vizConfig)
                .setType(VizType.LINE).setDisplayName("Closed Ticket Trend 7 days");
        Datacard datacard = new Datacard().setName("trendOfIssuesResolvedIn7DaysDC")
                .setDisplayName("Closed Ticket Trend 7 days")
                .setDescription("Trend of Closed Ticket in 7 days")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard userGrowthDC() {
        LineVizConfig vizConfig = new LineVizConfig();
        vizConfig.setName("userGrowthDC");
        SimpleQField monthField = new SimpleQField();
        monthField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Month").setType(QField.Type.DATASET)))
                .setAlias("Month");
        monthField.setDisplayFormat("text");

        SimpleQField userCountField = new SimpleQField();
        userCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("User Count").setType(QField.Type.DATASET)))
                .setAlias("User Count");
        userCountField.setDisplayFormat("number");

        var createdDateMonthFunc = new DateTruncQueryFunction();
        createdDateMonthFunc.setColumns(List.of(
                new QField("CreatedDate", QField.Type.ENTITY).setDataType("string")));
        createdDateMonthFunc.setTruncatedField("month");
        createdDateMonthFunc.setAlias("Created Date As Month").setDataType("string");

        QueryField createdDateMonth = new SimpleQField().setQueryFunction(createdDateMonthFunc)
                .setDescription("Created Date As Month").setDisplayFormat("text");

        vizConfig.setColumns(List.of(userCountField,createdDateMonth,monthField));
        vizConfig.setXAxis(monthField);
        vizConfig.setYAxis(List.of(userCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("user", List.of("Email", "FirstName", "LastName", "CreatedDate")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("userGrowthDC").setConfig(vizConfig)
                .setType(VizType.LINE).setDisplayName("User Growth");
        Datacard datacard = new Datacard().setName("userGrowthDC")
                .setDisplayName("User Growth")
                .setDescription("Monthly User Growth")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard sqlCountByOwnerDC() {
        TableVizConfig vizConfig = new TableVizConfig();
        vizConfig.setName("sqlCountByOwnerDC");
        SimpleQField ownerNameField = new SimpleQField();
        ownerNameField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Owner Name").setType(QField.Type.DATASET)))
                .setAlias("Owner Name");
        ownerNameField.setDisplayFormat("text");

        SimpleQField leadCountField = new SimpleQField();
        leadCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Qualified Lead Count").setType(QField.Type.DATASET)))
                .setAlias("Qualified Lead Count");
        leadCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(ownerNameField, leadCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("lead", List.of("Email", "leadSource", "Status", "CreatedDate", "OwnerId")));
        deps.add(new PipelineDependency("user", List.of("Email", "Name", "Alias")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("sqlCountByOwnerDC").setConfig(vizConfig)
                .setType(VizType.TABLE).setDisplayName("Qualified Leads by Owner");
        Datacard datacard = new Datacard().setName("sqlCountByOwnerDC")
                .setDisplayName("Qualified Leads by Owner")
                .setDescription("Leads in qualified status listed by owner")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard mqlCountInQuarterDC() {
        MetricVizConfig vizConfig = new MetricVizConfig();
        vizConfig.setName("mqlCountInQuarterDC");

        SimpleQField leadCountField = new SimpleQField();
        leadCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Lead Count").setType(QField.Type.DATASET)))
                .setAlias("Lead Count");
        leadCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(leadCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("lead", List.of("Email", "leadSource", "Status", "CreatedDate")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("mqlCountInQuarterDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Qualified Lead Count");
        Datacard datacard = new Datacard().setName("mqlCountInQuarterDC")
                .setDisplayName("Qualified Lead Count")
                .setDescription("Qualified Lead Count")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard leadsBySourceDC() {
        TableVizConfig vizConfig = new TableVizConfig();
        vizConfig.setName("leadsBySourceDC");
        SimpleQField leadSource = new SimpleQField();
        leadSource.getQueryFunction()
                .setColumns(List.of(new QField().setName("Lead Source").setType(QField.Type.DATASET)))
                .setAlias("Lead Source");
        leadSource.setDisplayFormat("text");

        SimpleQField leadCountField = new SimpleQField();
        leadCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Lead Count").setType(QField.Type.DATASET)))
                .setAlias("Lead Count");
        leadCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(leadSource, leadCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("lead", List.of("Email", "leadSource", "Status")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("leadsBySourceDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Accounts By Open Ticket Count");
        Datacard datacard = new Datacard().setName("leadsBySourceDC")
                .setDisplayName("Marketing Attribution Funnel")
                .setDescription("Distribution of leads by source")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard openTicketsByPriorityDC() {
        BarVizConfig vizConfig = new BarVizConfig();
        vizConfig.setName("openTicketsByPriorityDC");
        SimpleQField priorityField = new SimpleQField();
        priorityField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Priority").setType(QField.Type.DATASET)))
                .setAlias("Priority");
        priorityField.setDisplayFormat("text");

        SimpleQField ticketCountField = new SimpleQField();
        ticketCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Tickets Count").setType(QField.Type.DATASET)))
                .setAlias("Tickets Count");
        ticketCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(priorityField, ticketCountField));
        vizConfig.setXAxis(priorityField);
        vizConfig.setYAxis(List.of(ticketCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("openTicketsByPriorityDC").setConfig(vizConfig).setType(VizType.COLUMN).setDisplayName("Accounts By Open Ticket Count");
        Datacard datacard = new Datacard().setName("openTicketsByPriorityDC")
                .setDisplayName("Issues By Priority")
                .setDescription("The count of open issues by priority")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard accountsByOpenTicketCountDC() {
        VizConfig vizConfig = new TableVizConfig().setName("accountsByOpenTicketCountDC");
        SimpleQField accNamefield = new SimpleQField();
        accNamefield.getQueryFunction()
                .setColumns(List.of(new QField().setName("Account Name").setType(QField.Type.DATASET)))
                .setAlias("Account Name");
        accNamefield.setDisplayFormat("text");

        SimpleQField ticketCountField = new SimpleQField();
        ticketCountField.getQueryFunction()
                .setColumns(List.of(new QField().setName("Open Ticket Count").setType(QField.Type.DATASET)))
                .setAlias("Open Ticket Count");
        ticketCountField.setDisplayFormat("number");
        vizConfig.setColumns(List.of(accNamefield, ticketCountField));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at", "AccountId")));
        deps.add(new PipelineDependency("account", List.of("Name")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("accountsByOpenTicketCountDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Accounts By Open Ticket Count");
        Datacard datacard = new Datacard().setName("accountsByOpenTicketCountDC")
                .setDisplayName("Accounts By Open Ticket Count")
                .setDescription("Accounts By Open Ticket Count")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }

    private static Datacard openEscalatedTicketCountDC() {

        VizConfig vizConfig = new MetricVizConfig().setName("openEscalatedTicketCountDC");
        SimpleQField field = new SimpleQField();
        field.getQueryFunction()
                .setColumns(List.of(new QField().setName("Open Escalated Ticket Count").setType(QField.Type.DATASET)))
                .setAlias("Open Escalated Ticket Count");
        field.setDisplayFormat("number");
        vizConfig.setColumns(List.of(field));

        // create datacard with single visualization
        List<PipelineDependency> deps = new ArrayList<>();
        deps.add(new PipelineDependency("ticket", List.of("CaseNumber", "IsClosed", "Priority", "IsEscalated", "Status", "Type", "updated_at")));
        vizConfig.setPipelineDependencies(deps);
        Visualization visualization = new Visualization().setName("openEscalatedTicketCountDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Open Escalated Ticket Count");
        Datacard datacard = new Datacard().setName("openEscalatedTicketCountDC")
                .setDisplayName("Open Escalated Ticket Count")
                .setDescription("Open Escalated Ticket Count")
                .setContents(List.of(visualization))
                .setSeeded(true);

        return datacard;
    }
}
