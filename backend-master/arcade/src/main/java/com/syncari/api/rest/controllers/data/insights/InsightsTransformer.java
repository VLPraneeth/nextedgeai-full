package com.syncari.api.rest.controllers.data.insights;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.Tag;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.customer.DataCardAuthorConfigRepo;
import com.syncari.core.repositories.customer.DatacardRepo;
import com.syncari.core.repositories.customer.InsightsDashboardRepo;
import com.syncari.core.service.TagService;
import com.syncari.core.service.UserService;
import com.syncari.utils.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InsightsTransformer {

    @Autowired
    InsightsDashboardRepo dashboardRepo;
    @Autowired
    DatacardRepo datacardRepo;
    @Autowired
    DataCardAuthorConfigRepo authorConfigRepo;
    @Autowired
    TagService tagService;
    @Autowired
    DatasetTransformer datasetTransformer;
    @Autowired
    UserService userService;

    public DashboardDTO toDashboardDTO(InsightsDashboard dashboard, List<Datacard> datacards){
        if(dashboard == null) return null;
        DashboardDTO dto = new DashboardDTO()
                .setId(dashboard.getId())
                .setName(dashboard.getName())
                .setDisplayName(dashboard.getDisplayName())
                .setDescription(dashboard.getDescription())
                .setSeeded(dashboard.isSeeded())
                .setDraftStatus(dashboard.getDraftStatus())
                .setParentId(dashboard.getParentId());

        if(!StringUtils.isBlank(dashboard.getCreatedBy())){
            userService.findUserById(dashboard.getCreatedBy()).ifPresent(u -> dto.setCreatedBy(u.getName()));
        }
        if(!StringUtils.isBlank(dashboard.getUpdatedBy())){
            userService.findUserById(dashboard.getUpdatedBy()).ifPresent(u -> dto.setUpdatedBy(u.getName()));
        }

        if(dashboard.getCreatedAt() != null){
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(dashboard.getCreatedAt().toInstant(), ZoneOffset.UTC);
            dto.setCreatedAt(dateTime);
        }
        if(dashboard.getUpdatedAt() != null){
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(dashboard.getUpdatedAt().toInstant(), ZoneOffset.UTC);
            dto.setUpdatedAt(dateTime);
        }

        if(!datacards.isEmpty()){
            dto.setDataCards(datacards.stream().map(d -> toDatacardDTO(dashboard, d)).collect(Collectors.toList()));
        }
        dto.setTags(tagService.getTagNames(Taggable.dashboard, dashboard.getId()));
        return dto;
    }

    public InsightsDashboard toDashboard(DashboardDTO dto){
        InsightsDashboard dashboard = new InsightsDashboard()
                .setName(dto.getName())
                .setDisplayName(dto.getDisplayName())
                .setDescription(dto.getDescription());
        dashboard.setDraftStatus(dto.getDraftStatus() == null ? DraftStatus.NEW : dto.getDraftStatus());
        dashboard.setId(dto.getId());
        dto.getDataCards().forEach(datacardDTO -> {
            //Datacard datacard = toDatacard(datacardDTO);
            dashboard.getDataCardIds().add(datacardDTO.getId());

            DataCardSetting dcSetting = new DataCardSetting();
            dcSetting.setDatacardId(datacardDTO.getId());
            dcSetting.setLayout(toDatacardLayout(datacardDTO.getLayout()));
            // TODO set other settings for datacard
            dashboard.getDataCardSettings().add(dcSetting);
        });

        var tags = dto.getTags().stream()
                .map(t -> new Tag(t, true, Taggable.dashboard, dto.getId()))
                .collect(Collectors.toList());
        dashboard.setTags(tags);
        return dashboard;
    }


    public DatacardDTO toDatacardDTO(InsightsDashboard dashboard, Datacard datacard){
        // TODO - create a lighter version to populate the datacard list
        var dto =  new DatacardDTO()
                .setId(datacard.getId())
                .setName(datacard.getName())
                .setDisplayName(datacard.getDisplayName())
                .setDescription(datacard.getDescription())
                .setHidden(false)
                .setDraftStatus(datacard.getDraftStatus())
                .setSeeded(datacard.isSeeded());
        if (StringUtils.isNotEmpty(datacard.getId())){
            dto.setTags(tagService.getTagNames(Taggable.datacard, datacard.getId()));
        }
        dto.setErrorMsg(datacard.getErrorMsg());
        if(StringUtils.isEmpty(datacard.getErrorMsg())){
            dto.setContents(toVizDTO(datacard.getContents(), null, datacard.isSeeded()));
        }
        if(dashboard != null) {
            Optional<DataCardSetting> dcSettings = dashboard.getDataCardSettings().stream().filter(d -> d.getDatacardId().equals(datacard.getId())).findFirst();
            if(dcSettings.isPresent()){
                dto.setLayout(toLayoutDTO(dcSettings.get().getLayout()));
            } else {
                dto.setLayout(toLayoutDTO(getAuthorConfig(dashboard.getId(), datacard.getId()).getDataCardSetting().getLayout()));
            }
        }
        dto.setConfigurationMeta(toDatacardConfigMeta(datacard));
        dto.setConfiguration(toDatacardConfig(datacard));

        if(!StringUtils.isBlank(datacard.getCreatedBy())){
            userService.findUserById(datacard.getCreatedBy()).ifPresent(u -> dto.setCreatedBy(u.getName()));
        }
        if(!StringUtils.isBlank(datacard.getUpdatedBy())){
            userService.findUserById(datacard.getUpdatedBy()).ifPresent(u -> dto.setUpdatedBy(u.getName()));
        }

        if(datacard.getCreatedAt() != null){
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(datacard.getCreatedAt().toInstant(), ZoneOffset.UTC);
            dto.setCreatedAt(dateTime);
        }
        if(datacard.getUpdatedAt() != null){
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(datacard.getUpdatedAt().toInstant(), ZoneOffset.UTC);
            dto.setUpdatedAt(dateTime);
        }
        return dto;
    }


    public DataCardAuthorConfig getAuthorConfig(String dashboardId, String datacardId){
       return authorConfigRepo.findDataCardAuthorConfigByDashboardIdAndDatacardId(dashboardId, datacardId)
               .orElseThrow(() -> new RuntimeException("No Author config found"));
    }

    public LayoutDTO toLayoutDTO(DashboardLayout layout){
        return new LayoutDTO().setMaxH(layout.getMaxH()).setMinH(layout.getMinH())
                .setW(layout.getWidth()).setH(layout.getHeight())
                .setX(layout.getX()).setY(layout.getY()).setResizable(layout.isResizable());
    }

    public DashboardLayout toDatacardLayout(LayoutDTO layout){
        return new DashboardLayout().setMaxH(layout.getMaxH()).setMinH(layout.getMinH())
                .setWidth(layout.getW()).setHeight(layout.getH())
                .setX(layout.getX()).setY(layout.getY()).setResizable(layout.isResizable());
    }

    public VizDTO toVizDTO(List<Visualization> visualizations, VizData data, boolean isSeeded){
        // TODO: assume there is only one viz now. make changes for nested structure later
        if(visualizations == null || visualizations.isEmpty()) return null;
        Visualization v = visualizations.get(0);
        return new VizDTO().setId(v.getName()).setName(v.getDisplayName())
                .setDisplayName(v.getDisplayName())
                .setName(v.getName())
                //.setComponent(v.getType().name())
                .setConfiguration(toVizConfig(v, isSeeded))
                .setData(data);
    }

    private DatacardConfigDTO toDataCardConfigDTO(Datacard datacard) {
        var dto = new DatacardConfigDTO().setName(datacard.getName())
                .setDisplayName(datacard.getDisplayName());

        DateRange dateFilter = datacard.getConfiguration().getDateRange();
        if(dateFilter != null){
            dto.setDatetimeRange(dateFilter.getStart(), dateFilter.getEnd());
        }
        return dto;
    }

    private KeyValue toDatacardConfig(Datacard datacard){
        KeyValue config = new KeyValue();
        datacard.getContents().forEach(viz -> {
            viz.getConfig().getVariablesMap().forEach((k, v) -> {
                if(v.isUpdatable()){
                    config.put(k, datasetTransformer.toVariableValueDTO(v.getVariableValue()));
                }
            });
        });

        return config;
    }

    private List<DatacardConfigMeta> toDatacardConfigMeta(Datacard datacard) {
        // TODO: Currently its hardcoded to send datetime as configMeta but make it dynamic as more config comes in
        /*DatacardConfigMeta datetime = new DatacardConfigMeta("datetimeRange", "Time Frame", "datetimeRangePicker");
        boolean hasDateFilterSupport = datacard.getContents().stream().anyMatch(v -> v.getConfig().hasDateFilterSupport());
        return hasDateFilterSupport ? List.of(datetime) : Collections.emptyList();*/
        // get all variables and set configMeta
        List<DatacardConfigMeta> configMetaList = new ArrayList<>();
        datacard.getContents().forEach(viz -> {
            viz.getConfig().getVariablesMap().forEach((k, v) -> {
                if(v.isUpdatable()){
                    DatacardConfigMeta meta = new DatacardConfigMeta().setName(v.getApiName()).setDisplayName(v.getDisplayName())
                            .setComponent(v.getDatatype()).setDataType(v.getDatatype()).setHelpSummary(v.getHelpText())
                            .setMultiValueField(v.isMultiValueField());
                    configMetaList.add(meta);
                }
            });
        });
        return configMetaList;
    }

    public VizData toVizData(Visualization viz, List<Map<String, Object>> data) {
        switch (viz.getType()){
            case BAR:
            case COLUMN:
                BarVizConfig barVizConfig = (BarVizConfig) viz.getConfig();
                return buildChartVizData(barVizConfig.getSeries(), barVizConfig.getYAxis(), data, true);
            case LINE:
                LineVizConfig lineVizConfig = (LineVizConfig) viz.getConfig();
                return buildChartVizData(lineVizConfig.getSeries(), lineVizConfig.getYAxis(), data, true);
            case METRIC:
            case GAUGE:
            case TABLE:
                return buildChartVizData(null, viz.getConfig().getColumns(), data, false);
            case FUNNEL:
                FunnelVizConfig funnelVizConfig = (FunnelVizConfig) viz.getConfig();
                return buildChartVizData(null, List.of(funnelVizConfig.getMeasure(), funnelVizConfig.getDataField()), data, false);
            case PIE:
                PieVizConfig pieVizConfig = (PieVizConfig) viz.getConfig();
                List<QueryField> columns = new ArrayList<>();
                if (null != pieVizConfig.getCategory()) {
                    columns.add(pieVizConfig.getCategory());
                }
                if (null != pieVizConfig.getSubCategory()) {
                    columns.add(pieVizConfig.getSubCategory());
                }
                if (null != pieVizConfig.getValue()) {
                    columns.add(pieVizConfig.getValue());
                }
                return buildChartVizData(null, columns , data, false);
            default:
                throw new RuntimeException("Unsupported Visualization Type");
        }
    }

    private VizData buildChartVizData(List<QueryField> series,List<QueryField> yAxis, List<Map<String, Object>> data, boolean hasSeries){
        ChartVizData chartData = new ChartVizData();
        Set<String> seriesValues = new HashSet<>();
        List<KeyValue> rows = data.stream().map(record -> {
            if (CollectionUtils.isNotEmpty(series)){
                series.forEach(qF -> {
                    String alias = qF.getAlias();
                    Object value = record.get(alias);
                    if (null != value){
                        seriesValues.add(value.toString());
                    }else{
                        seriesValues.add("Other");
                    }
                });
            }

            KeyValue row = new KeyValue();
            row.putAll(record);
            return row;
        }).collect(Collectors.toList());
        List<Series> seriesList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(seriesValues)){
            seriesValues.forEach(s -> seriesList.add(new Series().setDisplayName(s)));
        } else {
            // no dynamic series, setting the value from the yAxis
            yAxis.forEach(f -> seriesList.add(new Series().setDisplayName(f.getAlias())));
        }
        if(hasSeries) {
            log.info("List of series added to chart data without any color is {}", seriesValues);
            chartData.setSeries(seriesList);
        }
        chartData.setRows(rows);
        return chartData;
    }

    public VizConfigDTO toVizConfig(Visualization viz, boolean isSeeded){
        VizConfigDTO vizConfig = new VizConfigDTO();
        vizConfig.setVizType(viz.getType());
        vizConfig.setDatasetId(viz.getConfig().getDatasetId());
        vizConfig.setVizLabel(viz.getConfig().getVizLabel());
        vizConfig.setVizLabelVisible(viz.getConfig().isVizLabelVisible());
        vizConfig.setVizLabelPosition(viz.getConfig().getVizLabelPosition());
        vizConfig.setVariablesMap(toVariableDTOMap(viz.getConfig().getDatasetId(), viz.getConfig().getVariablesMap()));
        if (null != viz.getConfig().getCategoryValues()){
            vizConfig.setCategoryValues(viz.getConfig().getCategoryValues());
        }
        switch (viz.getType()){
            case COLUMN:
            case BAR:
                BarVizConfig barVizConfig = (BarVizConfig) viz.getConfig();
                if(barVizConfig != null) {
                    vizConfig.setXAxis(toFieldProperty(barVizConfig.getXAxis(), isSeeded));
                    vizConfig.setYAxis(barVizConfig.getYAxis().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
                    if (CollectionUtils.isNotEmpty(barVizConfig.getSeries())) {
                        vizConfig.setSeries(barVizConfig.getSeries().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
                    }
                    vizConfig.setStacking(StackingType.none.equals(barVizConfig.getStacking()) ? null : barVizConfig.getStacking());
                    vizConfig.setColorTheme(barVizConfig.getColorTheme());
                    vizConfig.setLegendPosition(barVizConfig.getLegendPosition());
                }
                break;
            case LINE:
                LineVizConfig lineVizConfig = (LineVizConfig) viz.getConfig();
                vizConfig.setXAxis(toFieldProperty(lineVizConfig.getXAxis(), isSeeded));
                vizConfig.setYAxis(lineVizConfig.getYAxis().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
                if (CollectionUtils.isNotEmpty(lineVizConfig.getSeries())){
                    vizConfig.setSeries(lineVizConfig.getSeries().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
                }
                vizConfig.setStacking(StackingType.none.equals(lineVizConfig.getStacking()) ? null : lineVizConfig.getStacking());
                vizConfig.setColorTheme(lineVizConfig.getColorTheme());
                vizConfig.setLegendPosition(lineVizConfig.getLegendPosition());
                break;
            case PIE:
                return toPieVizConfigDTO(viz, isSeeded, vizConfig);
            case GAUGE:
                return toGaugeVizConfigDTO(viz, isSeeded, vizConfig);
            case FUNNEL:
                return toFunnelVizConfigDTO(viz, isSeeded, vizConfig);
            case METRIC:
                MetricVizConfig metricVizConfig = (MetricVizConfig)viz.getConfig();
                if (metricVizConfig.getColumns() != null) {
                    vizConfig.setColumns(metricVizConfig.getColumns().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
                }
                vizConfig.setRanges(metricVizConfig.getRanges());
                break;
            case TABLE:
                VizConfig tableVizConfig = viz.getConfig();
                if(tableVizConfig.getColumns() != null) {
                	vizConfig.setColumns(tableVizConfig.getColumns().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
                }
                break;
            default:
                throw new RuntimeException("Unsupported Visualization Type");
        }
        return vizConfig;
    }

    private FieldProperty toFieldProperty(QueryField field, boolean isSeeded){
        return new FieldProperty()
                .setColumn(field.getName())
                .setName(isSeeded ? field.getAlias() : field.getName())
                .setDisplayName(field.getAlias())
                .setDisplayFormat(field.getDisplayFormat())
                .setColor(field.getColor());
    }

    public Datacard toDatacard(DatacardDTO datacardDTO){
        Datacard datacard = new Datacard();
        datacard.setId(datacardDTO.getId());
        datacard.setName(datacardDTO.getName());
        datacard.setDisplayName(datacardDTO.getDisplayName());
        datacard.setDescription(datacardDTO.getDescription());
        datacard.setContents(toVisualization(datacardDTO.getContents()));
        /*if(datacardDTO.getConfiguration() != null) {
        	datacard.setConfiguration(toDatacardConfig(datacardDTO.getConfiguration()));
        }*/
        datacard.setDraftStatus(datacardDTO.getDraftStatus() == null ? DraftStatus.NEW : datacardDTO.getDraftStatus());
        var tags = datacardDTO.getTags().stream()
                .map(t -> new Tag(t, true, Taggable.datacard, datacardDTO.getId()))
                .collect(Collectors.toList());
        datacard.setTags(tags);

        return datacard;
    }

    private DatacardConfig toDatacardConfig(DatacardConfigDTO dto){
        DatacardConfig config = new DatacardConfig();
        config.setDateRange(dto.getDatetimeRange());
        return config;
    }

    private List<Visualization> toVisualization(VizDTO vizDTO){
        List<Visualization> visualizations = new ArrayList<>();
        if(vizDTO == null){
            return visualizations;
        }
        Visualization v = new Visualization();
        v.setName(vizDTO.getName());
        v.setDisplayName(vizDTO.getDisplayName());
        v.setType(vizDTO.getConfiguration().getVizType());
        v.setConfig(toVizConfig(vizDTO.getConfiguration(), v.getType()));
        visualizations.add(v);
        visualizations.addAll(toVisualization(vizDTO.getContents()));
        return visualizations;
    }

    private VizConfig toVizConfig(VizConfigDTO config, VizType type){
        switch (type){
            case COLUMN:
            case BAR:
                BarVizConfig barVizConfig = new BarVizConfig();
                // TODO: set more configurable properties when available
                barVizConfig.setXAxis(new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(config.getXAxis().getName()).setType(QField.Type.DATASET)))
                        .setAlias(config.getXAxis().getDisplayName())).setDisplayFormat(config.getXAxis().getDisplayFormat()).setColor(config.getXAxis().getColor()))
                        .setYAxis(config.getYAxis().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()))
                        .setStacking(config.getStacking())
                        .setLegendPosition(config.getLegendPosition())
                        .setColorTheme(config.getColorTheme())
                        .setColumns(config.getYAxis().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()))
                        .setDatasetId(config.getDatasetId())
                        .setVizLabel(config.getVizLabel())
                        .setVizLabelVisible(config.isVizLabelVisible())
                        .setVizLabelPosition(config.getVizLabelPosition());
                if(config.getDateFilter() != null) {
                    barVizConfig.setDateFilter(new DateFilter()
                            .setField(new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(config.getDateFilter().getFieldName()).setType(QField.Type.DATASET)))))
                            .setDateRange(new DateRange(config.getDateFilter().getStartDate(), config.getDateFilter().getEndDate()))
                    );
                }
                
                barVizConfig.setVariablesMap(toVariableMap(config.getVariablesMap()));
                if (null != config.getSeries()) {
                    barVizConfig.setSeries(config.getSeries().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
                }
                if (null != config.getCategoryValues()){
                    barVizConfig.setCategoryValues(config.getCategoryValues());
                }
                return barVizConfig;
            case LINE:
                LineVizConfig lineVizConfig = new LineVizConfig();
                // TODO: set more configurable properties when available
                lineVizConfig.setXAxis(new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(config.getXAxis().getName()).setType(QField.Type.DATASET))).setAlias(config.getXAxis().getDisplayName())).setDisplayFormat(config.getXAxis().getDisplayFormat()).setColor(config.getXAxis().getColor()))
                        .setYAxis(config.getYAxis().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()))
                        .setStacking(config.getStacking())
                        .setLegendPosition(config.getLegendPosition())
                        .setColorTheme(config.getColorTheme())
                        .setColumns(config.getYAxis().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()))
                        .setDatasetId(config.getDatasetId())
                        .setVizLabel(config.getVizLabel())
                        .setVizLabelVisible(config.isVizLabelVisible())
                        .setVizLabelPosition(config.getVizLabelPosition());
                if(config.getDateFilter() != null) {
                    lineVizConfig.setDateFilter(new DateFilter()
                            .setField(new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(config.getDateFilter().getFieldName()).setType(QField.Type.DATASET)))))
                            .setDateRange(new DateRange(config.getDateFilter().getStartDate(), config.getDateFilter().getEndDate()))
                    );
                }
                lineVizConfig.setVariablesMap(toVariableMap(config.getVariablesMap()));
                if (null != config.getSeries()) {
                    lineVizConfig.setSeries(config.getSeries().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
                }
                if (null != config.getCategoryValues()){
                    lineVizConfig.setCategoryValues(config.getCategoryValues());
                }
                return lineVizConfig;
            case PIE:
                return toPieVizConfig(config);
            case GAUGE:
                return toGaugeVizConfig(config);
            case FUNNEL:
                return toFunnelVizConfig(config);
            case TABLE:
                TableVizConfig tableVizConfig = new TableVizConfig();
                // TODO: set more configurable properties when available
                tableVizConfig.setColumns(config.getColumns().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
                tableVizConfig.setDatasetId(config.getDatasetId())
                    .setVizLabel(config.getVizLabel())
                    .setVizLabelVisible(config.isVizLabelVisible())
                    .setVizLabelPosition(config.getVizLabelPosition());
                if(config.getDateFilter() != null) {
                    tableVizConfig.setDateFilter(new DateFilter()
                            .setField(new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(config.getDateFilter().getFieldName()).setType(QField.Type.DATASET)))))
                            .setDateRange(new DateRange(config.getDateFilter().getStartDate(),config.getDateFilter().getEndDate()))
                    );
                }
                tableVizConfig.setVariablesMap(toVariableMap(config.getVariablesMap()));
                if (null != config.getCategoryValues()){
                    tableVizConfig.setCategoryValues(config.getCategoryValues());
                }
                return tableVizConfig;
            case METRIC:
                MetricVizConfig metricVizConfig = new MetricVizConfig();
                // TODO: set more configurable properties when available
                metricVizConfig.setColumns(config.getColumns().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
                metricVizConfig.setDatasetId(config.getDatasetId())
                    .setVizLabel(config.getVizLabel())
                    .setVizLabelVisible(config.isVizLabelVisible())
                    .setVizLabelPosition(config.getVizLabelPosition());
                if(config.getDateFilter() != null) {
                    metricVizConfig.setDateFilter(new DateFilter()
                            .setField(new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(config.getDateFilter().getFieldName()).setType(QField.Type.DATASET)))))
                            .setDateRange(new DateRange(config.getDateFilter().getStartDate(),config.getDateFilter().getEndDate()))
                    );
                }
                metricVizConfig.setRanges(config.getRanges());
                metricVizConfig.setVariablesMap(toVariableMap(config.getVariablesMap()));
                if (null != config.getCategoryValues()){
                    metricVizConfig.setCategoryValues(config.getCategoryValues());
                }
                return metricVizConfig;
            default:
                throw new RuntimeException("Unsupported Visualization Type");
        }
    }

    private PieVizConfig toPieVizConfig(VizConfigDTO config) {
        PieVizConfig pieVizConfig = new PieVizConfig();
        pieVizConfig.setDatasetId(config.getDatasetId())
                .setVizLabel(config.getVizLabel())
                .setVizLabelVisible(config.isVizLabelVisible())
                .setVizLabelPosition(config.getVizLabelPosition())
                .setVariablesMap(toVariableMap(config.getVariablesMap()));

        if (null != config.getValue()) {
            pieVizConfig.setValue(new SimpleQField().setQueryFunction(
                    new NoQueryFunction().setColumns(
                            List.of(
                                    new QField().setName(config.getValue().getName()).setType(QField.Type.DATASET)
                            )
                    ).setAlias(config.getValue().getDisplayName())
            ).setDisplayFormat(config.getValue().getDisplayFormat()).setColor(config.getValue().getColor()));
        }

        if (null != config.getCategory()) {
            pieVizConfig.setCategory(new SimpleQField().setQueryFunction(
                    new NoQueryFunction().setColumns(
                            List.of(
                                    new QField().setName(config.getCategory().getName()).setType(QField.Type.DATASET)
                            )
                    ).setAlias(config.getCategory().getDisplayName())
            ).setDisplayFormat(config.getCategory().getDisplayFormat()).setColor(config.getCategory().getColor()));
        }

        if (null != config.getSubCategory()) {
            pieVizConfig.setSubCategory(new SimpleQField().setQueryFunction(
                    new NoQueryFunction().setColumns(
                            List.of(
                                    new QField().setName(config.getSubCategory().getName()).setType(QField.Type.DATASET)
                            )
                    ).setAlias(config.getSubCategory().getDisplayName())
            ).setDisplayFormat(config.getSubCategory().getDisplayFormat()).setColor(config.getSubCategory().getColor()));
        }

        if (null != config.getColumns()) {
            pieVizConfig.setColumns(config.getColumns().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
        }
        if (null != config.getCategoryValues()){
            pieVizConfig.setCategoryValues(config.getCategoryValues());
        }

        pieVizConfig.setMinimumValue(config.getMinimumValue());
        pieVizConfig.setLegendVisible(config.isLegendVisible());
        pieVizConfig.setLabelVisible(config.isLabelVisible());
        pieVizConfig.setLegendPosition(config.getLegendPosition());
        return pieVizConfig;
    }

    private VizConfigDTO toPieVizConfigDTO(Visualization viz, boolean isSeeded, VizConfigDTO vizConfig) {
        PieVizConfig pieVizConfig = (PieVizConfig) viz.getConfig();
        if (null != pieVizConfig.getCategory()) {
            vizConfig.setCategory(toFieldProperty(pieVizConfig.getCategory(), isSeeded));
        }
        if (null != pieVizConfig.getSubCategory()) {
            vizConfig.setSubCategory(toFieldProperty(pieVizConfig.getSubCategory(), isSeeded));
        }
        if (null != pieVizConfig.getValue()) {
            vizConfig.setValue(toFieldProperty(pieVizConfig.getValue(), isSeeded));
        }
        vizConfig.setLegendPosition(pieVizConfig.getLegendPosition());
        vizConfig.setMinimumValue(pieVizConfig.getMinimumValue());
        vizConfig.setLegendVisible(pieVizConfig.isLegendVisible());
        vizConfig.setLabelVisible(pieVizConfig.isLabelVisible());
        if(null != pieVizConfig.getColumns()) {
            vizConfig.setColumns(pieVizConfig.getColumns().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
        }
        return vizConfig;
    }

    private GaugeVizConfig toGaugeVizConfig(VizConfigDTO config) {
        GaugeVizConfig gaugeVizConfig = new GaugeVizConfig();
        gaugeVizConfig.setDatasetId(config.getDatasetId())
            .setVariablesMap(toVariableMap(config.getVariablesMap()))
            .setVizLabel(config.getVizLabel())
            .setVizLabelVisible(config.isVizLabelVisible())
            .setVizLabelPosition(config.getVizLabelPosition());

        if (null != config.getColumns()) {
            gaugeVizConfig.setColumns(config.getColumns().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
        }
        gaugeVizConfig.setLegendPosition(config.getLegendPosition());
        gaugeVizConfig.setRanges(config.getRanges());
        if (null != config.getCategoryValues()){
            gaugeVizConfig.setCategoryValues(config.getCategoryValues());
        }

        return gaugeVizConfig;
    }

    private VizConfigDTO toGaugeVizConfigDTO(Visualization viz, boolean isSeeded, VizConfigDTO vizConfig) {
        GaugeVizConfig gaugeVizConfig = (GaugeVizConfig) viz.getConfig();
        vizConfig.setRanges(gaugeVizConfig.getRanges());
        vizConfig.setLegendPosition(gaugeVizConfig.getLegendPosition());
        if(null != gaugeVizConfig.getColumns()) {
            vizConfig.setColumns(gaugeVizConfig.getColumns().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
        }
        return vizConfig;
    }

    private FunnelVizConfig toFunnelVizConfig(VizConfigDTO config) {
        FunnelVizConfig funnelVizConfig = new FunnelVizConfig();
        funnelVizConfig.setDatasetId(config.getDatasetId())
            .setVariablesMap(toVariableMap(config.getVariablesMap()))
            .setVizLabel(config.getVizLabel())
            .setVizLabelVisible(config.isVizLabelVisible())
            .setVizLabelPosition(config.getVizLabelPosition());

        if (null != config.getColumns()) {
            funnelVizConfig.setColumns(config.getColumns().stream().map(f -> new SimpleQField().setQueryFunction(new NoQueryFunction().setColumns(List.of(new QField().setName(f.getName()).setType(QField.Type.DATASET))).setAlias(f.getDisplayName())).setDisplayFormat(f.getDisplayFormat()).setColor(f.getColor())).collect(Collectors.toList()));
        }
        if (null != config.getMeasure()) {
            funnelVizConfig.setMeasure(new SimpleQField().setQueryFunction(
                    new NoQueryFunction().setColumns(
                            List.of(
                                    new QField().setName(config.getMeasure().getName()).setType(QField.Type.DATASET)
                            )
                    ).setAlias(config.getMeasure().getDisplayName())
                    ).setDisplayFormat(config.getMeasure().getDisplayFormat())
            );

        }
        if (null != config.getDataField()) {
            funnelVizConfig.setDataField(new SimpleQField().setQueryFunction(
                    new NoQueryFunction().setColumns(
                            List.of(
                                    new QField().setName(config.getDataField().getName()).setType(QField.Type.DATASET)
                            )
                    ).setAlias(config.getDataField().getDisplayName())
                    ).setDisplayFormat(config.getDataField().getDisplayFormat())
            );
        }
        funnelVizConfig.setLegendPosition(config.getLegendPosition());
        funnelVizConfig.setStages(config.getStages());
        funnelVizConfig.setSortBy(config.getSortBy());
        funnelVizConfig.setAscending(config.isAscending());
        funnelVizConfig.setLabelVisible(config.isLabelVisible());
        funnelVizConfig.setLabelPosition(config.getLabelPosition());
        funnelVizConfig.setLegendVisible(config.isLegendVisible());
        funnelVizConfig.setColorTheme(config.getColorTheme());
        funnelVizConfig.setDisplayAdditional(config.getDisplayAdditional());
        if (null != config.getCategoryValues()){
            funnelVizConfig.setCategoryValues(config.getCategoryValues());
        }

        return funnelVizConfig;
    }

    private VizConfigDTO toFunnelVizConfigDTO(Visualization viz, boolean isSeeded, VizConfigDTO vizConfig) {
        FunnelVizConfig funnelVizConfig = (FunnelVizConfig) viz.getConfig();

        if (null != funnelVizConfig.getMeasure()) {
            vizConfig.setMeasure(toFieldProperty(funnelVizConfig.getMeasure(), isSeeded));
        }

        if (null != funnelVizConfig.getDataField()) {
            vizConfig.setDataField(toFieldProperty(funnelVizConfig.getDataField(), isSeeded));
        }

        if (null != funnelVizConfig.getColumns()) {
            vizConfig.setColumns(funnelVizConfig.getColumns().stream().map(c -> toFieldProperty(c, isSeeded)).collect(Collectors.toList()));
        }
        vizConfig.setLegendPosition(funnelVizConfig.getLegendPosition());
        vizConfig.setStages(funnelVizConfig.getStages());
        vizConfig.setSortBy(funnelVizConfig.getSortBy());
        vizConfig.setAscending(funnelVizConfig.isAscending());
        vizConfig.setLabelPosition(funnelVizConfig.getLabelPosition());
        vizConfig.setLegendVisible(funnelVizConfig.isLegendVisible());
        vizConfig.setLabelVisible(funnelVizConfig.isLabelVisible());
        vizConfig.setColorTheme(funnelVizConfig.getColorTheme());
        vizConfig.setDisplayAdditional(funnelVizConfig.getDisplayAdditional());

        return vizConfig;
    }

    private Map<String, Variable> toVariableMap(Map<String, VariableDTO> variableDTOMap){
        Map<String, Variable> varMap = new HashMap<>();
        variableDTOMap.forEach((k, v) -> {
            varMap.put(k, datasetTransformer.toVariable(v));
        });

        return varMap;
    }

    private Map<String, VariableDTO> toVariableDTOMap(String datasetId, Map<String, Variable> variableMap){
        Map<String, VariableDTO> varDTOMap = new HashMap<>();
        variableMap.forEach((k, v) -> {
            varDTOMap.put(k, datasetTransformer.toVariableDTO(Optional.ofNullable(datasetId), v));
        });

        return varDTOMap;
    }

}
