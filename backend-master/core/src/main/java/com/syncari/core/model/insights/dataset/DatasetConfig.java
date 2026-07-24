package com.syncari.core.model.insights.dataset;

import com.syncari.core.model.insights.*;
import com.syncari.utils.I18n;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Data
@Accessors(chain = true)
@Slf4j
public class DatasetConfig {

    private List<Projection> projectionsList;
    private List<DatasetFrom> fromDatasets = new ArrayList<>();
    private List<Join> join = new ArrayList<>();
    private List<AggregateConfig> aggregate = new ArrayList<>();
    private Map<String, Object> predicate;
    private List<Sort> order = new ArrayList<>();
    private int limit;
    private boolean isGroup;
    private ConfigMode configMode = ConfigMode.BASIC;

    public enum ConfigMode{
        BASIC,
        SQL
    }

    public DatasetConfig makeCopy() {
        DatasetConfig newConfig = new DatasetConfig().setPredicate(this.getPredicate()).setGroup(this.isGroup)
                .setOrder(this.getOrder()).setAggregate(this.getAggregate()).setFromDatasets(this.getFromDatasets())
                .setJoin(this.getJoin()).setLimit(this.getLimit()).setProjectionsList(this.getProjectionsList());
        return newConfig;
    }
    public void validate(){
        validateCondition(CollectionUtils.isEmpty(getFromDatasets()), I18n.i18n("dataset_from_empty"));
        List<DatasetFrom> datasetFromDTOS = getFromDatasets();
        if (CollectionUtils.isNotEmpty(datasetFromDTOS)){
            datasetFromDTOS.forEach(df -> {
                validateCondition(StringUtils.isEmpty(df.getDisplayName()), I18n.i18n("from_name_empty"));
            });
        }
        validateProjections();
        validateJoin();
        if (CollectionUtils.isNotEmpty(this.getAggregate())){
            validateAggregate();
        }
    }

    public void validateProjections(){
        validateCondition((CollectionUtils.isEmpty(getProjectionsList())), I18n.i18n("dataset_projections_empty"));
        List<Projection> datasetProjections = getProjectionsList();
        datasetProjections.forEach(pro -> {
            log.info("Projection {} validation happening ",pro);
            validateCondition((null == pro.getFunction()), I18n.i18n("projection_aggfunction_empty"));
            validateCondition(!pro.getFunction().validate(), I18n.i18n("projection_aggfunction_wrongfields"));
        });
    }
    public void validateAggregate(){
        List<Projection> selectedColumns = new ArrayList<>();
        List<Projection> calculatedColumns = new ArrayList<>();
        projectionsList.forEach(p -> {
            if(p.getFunction() instanceof NoQueryFunction){
                // if no query function its
                selectedColumns.add(p);
            } else {
                calculatedColumns.add(p);
            }
        });

        Set<String> calculatedFieldAliasNames = calculatedColumns.stream().map(c -> c.getAliasName()).collect(Collectors.toSet());
        Set<String> selectedColumnAliases = selectedColumns.stream().map(s -> s.getAliasName()).collect(Collectors.toSet());
        Set<String> selectedColumnNames = selectedColumns.stream().map(s -> s.getFunction().getColumns().get(0).getName()).collect(Collectors.toSet());
        aggregate.forEach(agg -> {
            // validate if aggregate config provided is syntactically correct
            agg.validate();
            // match alias names for calculated fields and QField for selected fields
            validateCondition((!calculatedFieldAliasNames.contains(agg.getAggregateField().getName()) // calculated field must match alias name
                            && !selectedColumnAliases.contains(agg.getAggregateField().getName()) && !selectedColumnNames.contains(agg.getAggregateField().getName())), // selected field must match the QField
                    i18n("error_dataset_aggregate_invalid_field", agg.getAggregateField().getName()));

        });
    }

    public void validateJoin() {
        List<String> selectedDatasetIds = fromDatasets.stream().map(d -> d.getDatasetId()).collect(Collectors.toList());
        join.forEach(join -> {
            join.validate();
            validateCondition(!selectedDatasetIds.contains(join.getDatasetFieldFrom().getDatasetId()), i18n("error_dataset_join"));
        });
    }

    public QueryConfig toQueryConfig() {
        DatasetConfig datasetConfig = this;
        assert (null != datasetConfig);

        List<Projection> projectionList = datasetConfig.getProjectionsList();
        Map<String, String> aliasMap = new HashMap<>();
        datasetConfig.getFromDatasets().forEach(df -> {
            if (StringUtils.isNotEmpty(df.getAlias())) {
                aliasMap.put(df.getDatasetId(), df.getAlias());
            } else {
                aliasMap.put(df.getDatasetId(), df.getDatastoreName());
            }
        });
        QueryConfig queryConfig = new QueryConfig().setColumns(toQueryFieldsFromProjectionList(projectionList, null, aliasMap));
        queryConfig.setFromDatasets(datasetConfig.getFromDatasets());
        Map<String, Object> predicateFromDataset = datasetConfig.getPredicate();

        // set grouping columns
        queryConfig.setGroupingColumns(datasetConfig.getAggregate());
        Map<String, Object> predicateMap = new HashMap<>();

        if (CollectionUtils.isNotEmpty(datasetConfig.getOrder())) {
            queryConfig.setSortList(datasetConfig.getOrder());
        }
        // Map date filter to QueryConfig predicate And operator
        if (MapUtils.isNotEmpty(predicateFromDataset)) {
            predicateMap.putAll(predicateFromDataset);
            queryConfig.setPredicate(predicateMap);
        }
        List<Join> joins = datasetConfig.getJoin();
        if (CollectionUtils.isNotEmpty(joins) && (null != joins.stream().findFirst().get().getDatasetFieldFrom())) {
            queryConfig.setJoins(joins);
        }

        int limit = datasetConfig.getLimit();
        if (limit > 0) {
            queryConfig.setLimit(limit);
        }
        return queryConfig;
    }

    private List<QueryField> toQueryFieldsFromProjectionList(List<Projection> projections, String displayFormat, Map<String, String> entityIdWithAlias) {
        List<QueryField> queryFields = new LinkedList<>();
        projections.forEach(projection -> {
            QueryFunction queryFunction = projection.getFunction();
            if (queryFunction instanceof NoQueryFunction) {
                QueryField field = new SimpleQField();
                field.setQueryFunction(queryFunction);
                field.setDisplayFormat(displayFormat);
                queryFields.add(field);
            } else {
                QueryField field = new ComplexQField();
                field.setQueryFunction(queryFunction);
                field.setDisplayFormat(displayFormat);
                queryFields.add(field);
            }
            if (StringUtils.isEmpty(queryFunction.getAlias())) {
                queryFunction.setAlias(queryFunction.buildExpression("\"", entityIdWithAlias).replace("\"", "").trim());
            }
        });
        return queryFields;
    }
}
