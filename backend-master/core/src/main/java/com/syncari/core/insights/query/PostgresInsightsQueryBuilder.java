package com.syncari.core.insights.query;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.database.PostgresService;
import com.syncari.connector.service.query.SqlQueries;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.insights.InsightsQueryPredicateParser;
import com.syncari.core.insights.QueryBuilderVisitor;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.DatasetRepo;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Component
@Slf4j
public class PostgresInsightsQueryBuilder extends PostgresService implements InsightsQueryBuilder{

    @Autowired
    SchemaService schemaService;

    @Autowired
    DatasetRepo datasetRepo;

    @Override
    public String buildQuery(QueryConfig queryConfig, ConnectorInfo connectorInfo, Optional<String> datasetId
            , Map<String, VariableValue> variableValuesMapTobeused, Map<String, Datatype> variableLeftDataTypes) {
       if (null == queryConfig){
            return null;
        }
        StringBuilder resultQuery = new StringBuilder();
       Map<String, String> aliasMap = new HashMap<>();
       // defining this way so can be used in lamda expressions
        Map<String, String> fromName = aliasMap;
        List<DatasetFrom> fromDatasets = queryConfig.getFromDatasets();
        if (CollectionUtils.isNotEmpty(fromDatasets)){
            fromDatasets.forEach(fr -> {
                fromName.put(fr.getDatasetId(), StringUtils.isNotEmpty(fr.getAlias()) ? fr.getAlias() : fr.getApiName());
            });
        }

        Set<String> datasetIds = new HashSet<>();
        datasetId.ifPresent(d -> datasetIds.add(d));
        getAllVariablesValue(datasetIds, variableValuesMapTobeused);
        log.debug("Variables data found in dataset {} is {}", queryConfig.getFromDatasets(),variableValuesMapTobeused);

        StringBuilder fields = new StringBuilder();

        // child query config
        // Todo old code needs to be removed after moving old dashboards to new one
        if (null != queryConfig.getChildQueryConfig()){
            String nestedQuery = buildQuery(queryConfig.getChildQueryConfig(), connectorInfo, datasetId, variableValuesMapTobeused,variableLeftDataTypes);
            Map<String, Object> predMap =  queryConfig.getPredicate();
            if (MapUtils.isNotEmpty(predMap)){
                Map<String, Object> rightMap = (Map<String, Object>) predMap.get("right");
                if (MapUtils.isNotEmpty(rightMap) && ((String)rightMap.get("type")).equalsIgnoreCase("vizconfig")){
                    predMap.put("right", Map.of("type", "vizconfig","value", nestedQuery ));
                }else{
                    List<Map<String, Object>> predicatesList = (List<Map<String, Object>>) predMap.get("predicates");
                    List<Map<String, Object>> outputlist = predicatesList.stream().filter(preds -> {
                        if (preds.containsKey("right")){
                            Map<String, Object> vizConfigRightMap = (Map<String, Object>)preds.get("right");
                            return (vizConfigRightMap.containsKey("type") && (((String)vizConfigRightMap.get("type")).equals("vizconfig")));
                        }else{
                            return false;
                        }
                    }).collect(Collectors.toList());
                    // Only one is expected
                    Optional<Map<String, Object>> firstElement =  outputlist.stream().findFirst();
                    firstElement.ifPresent(vizConfigMap -> vizConfigMap.put("right", Map.of("type", "vizconfig","value", nestedQuery )));
                }
            }
        }
        // projections
        if ((CollectionUtils.isNotEmpty(queryConfig.getColumns())) && ((MapUtils.isNotEmpty(fromName)) || (CollectionUtils.isNotEmpty(fromDatasets)))){
            List<QueryField> allFields = queryConfig.getColumns();
            List<String> columns = new ArrayList<>();
            List<String> projectionsAlias = new ArrayList<>();
            allFields.forEach(f -> {
                QueryFunction func = f.getQueryFunction();
                columns.add(func.buildExpression(getEscapeChar(), fromName));
                projectionsAlias.add(f.getAlias());
            });
            if (CollectionUtils.isNotEmpty(columns)){
                fields.append(StringUtils.join(columns,","));
            }

            Set<String> tableNames = new LinkedHashSet<>();
            Map<String, Pair<String, List<AttributeDefinition>>> datasetIdToApiNameMap = new LinkedHashMap<>();
            Map<String, Pair<String, List<AttributeDefinition>>> datasetIdToTableNameMap = new LinkedHashMap<>();

            if (CollectionUtils.isNotEmpty(fromDatasets)){
                fromDatasets.forEach(from -> {
                    if (from.getDatasetType() == DatasourceType.ENTITY){
                        Optional<EntityDefinition> ef = ObjectId.isValid(from.getDatasetId()) ? schemaService.findEntity(from.getDatasetId()) : Optional.empty();
                        ef.ifPresentOrElse(e -> {
                            String datastoreName =  e.getDataStoreName().toLowerCase();
                            String entityAliasName = StringUtils.isNotEmpty(from.getAlias())? from.getAlias() : from.getApiName();
                            List<AttributeDefinition> def =  e.getAttributes();
                            def.add(new AttributeDefinition().setDisplayName("Syncari Id").setDataType(StringType.VALUE).setApiName("syncariid").setEntityId(e.getId()).setDataStoreName("syncariid"));
                            String tableName = getTableName(datastoreName, connectorInfo);
                            String alias = StringUtils.isNotEmpty(from.getAlias())?from.getAlias():from.getApiName();
                            tableNames.add(tableName + " \"" + alias +"\"");
                            datasetIdToApiNameMap.put(from.getDatasetId(),Pair.of(entityAliasName, def));
                            datasetIdToTableNameMap.put(from.getDatasetId(),Pair.of(datastoreName, def));

                        }, () -> {
                            throw new SyncariValidationException(String.format(i18n("entity_id_doesnot_exists"),from.getDatasetId()));
                        });
                    }else if (from.getDatasetType() == DatasourceType.DATASET){
                        String innerDatasetId = from.getDatasetId();
                        String aliasName = StringUtils.isNotEmpty(from.getAlias()) ? from.getAlias() : from.getApiName();
                        Optional<Dataset> innerDataset = Optional.empty();
                        if (StringUtils.isNotEmpty(innerDatasetId)){
                            innerDataset = datasetRepo.findById(innerDatasetId);
                        }
                        if (!innerDataset.isPresent()) {
                            innerDataset = Optional.of(from.getDataset());
                        }
                        innerDataset.ifPresentOrElse(iD -> {
                            String innerQuery;
                            if (iD.isSQLMode() && StringUtils.isNotEmpty(iD.getRawQuery())){
                                innerQuery = iD.getRawQuery();
                            }else{
                                QueryConfig innerQueryConfig = buildQueryConfigFromDataset(iD);
                                innerQuery = buildQuery(innerQueryConfig, connectorInfo,Optional.of(innerDatasetId), variableValuesMapTobeused,variableLeftDataTypes);
                            }
                            tableNames.add(" (" + innerQuery + ") \"" + aliasName+"\"");
                            datasetIdToApiNameMap.put(from.getDatasetId(),Pair.of(" (" + innerQuery + ")", List.of()));
                            datasetIdToTableNameMap.put(from.getDatasetId(),Pair.of(" (" + innerQuery + ")", List.of()));
                        },() -> {
                            throw new SyncariValidationException("Missing inner dataset");
                        });
                    }else{
                        throw new SyncariValidationException(String.format("Not supported type of dataset %s",from.getDatasetType()));
                    }
                });
            }



            String allTableNames = null;
            // Joins
            // get All joins
            // for each join find the alias from from entityid with alias
            // use alias and field name to store  and api names for making a join
            if (CollectionUtils.isNotEmpty(queryConfig.getJoins()) && CollectionUtils.isNotEmpty(tableNames)){
                Join firstJoin = queryConfig.getJoins().get(0);
                String fromTableName = firstJoin.getDatasetFieldFrom().getType() != QField.Type.DATASET ? getTableName(datasetIdToTableNameMap.get(firstJoin.getDatasetFieldFrom().getDatasetId()).x, connectorInfo)
                        :datasetIdToTableNameMap.get(firstJoin.getDatasetFieldFrom().getDatasetId()).x;
                Set<String> tablesNamesUsed = new HashSet<>();

                String fromAlias = StringUtils.isNotEmpty(firstJoin.getDatasetFieldFrom().getDatasourceAlias())? firstJoin.getDatasetFieldFrom().getDatasourceAlias()
                            : fromName.get(firstJoin.getDatasetFieldFrom().getDatasetId());
                resultQuery.append(String.format(SqlQueries.SELECT_ALL, fields, fromTableName + " \"" + fromAlias +"\"" ));
                tablesNamesUsed.add(fromTableName+ " \"" + fromAlias +"\"" );

                queryConfig.getJoins().forEach(j -> {
                    QField fieldFrom = j.getDatasetFieldFrom();
                    String fieldFromName = fieldFrom.getName();
                    QField fieldTo = j.getDatasetFieldTo();
                    String fieldToName = fieldTo.getName();

                    String datasetIdOfFromField = fieldFrom.getDatasetId();
                    String fromAliasName = StringUtils.isNotEmpty(fieldFrom.getDatasourceAlias()) ? "\"" + fieldFrom.getDatasourceAlias() + "\"" : "\"" + fromName.getOrDefault(datasetIdOfFromField,fieldFromName)+ "\"";
                    String datasetIdOfToField = fieldTo.getDatasetId();
                    String toAliasName = StringUtils.isNotEmpty(fieldTo.getDatasourceAlias())? "\"" + fieldTo.getDatasourceAlias()+ "\""
                            : "\"" + fromName.get(fieldTo.getDatasetId())+ "\"" ;
                    final String fromFldName = fieldFromName;
                    final String toFldName = fieldToName;
                    List<AttributeDefinition> fromAttributeDef = datasetIdToTableNameMap.getOrDefault(datasetIdOfFromField, Pair.of(fromAliasName, List.of())).y.stream().filter(att -> att.getApiName().equals(fromFldName)).collect(Collectors.toList());
                    List<AttributeDefinition> toAttributeDef = datasetIdToTableNameMap.getOrDefault(datasetIdOfToField, Pair.of(toAliasName, List.of())).y.stream().filter(att -> att.getApiName().equals(toFldName)).collect(Collectors.toList());
                    Optional<AttributeDefinition> firstFromAttribDef = fromAttributeDef.stream().findFirst();
                    Optional<AttributeDefinition> firstToAttribDef = toAttributeDef.stream().findFirst();

                    if (firstFromAttribDef.isPresent() && firstToAttribDef.isPresent()){
                        if (firstFromAttribDef.get().isIdField() && firstToAttribDef.get().isReference()){
                            fieldFromName = "syncariid";
                        }
                        if (firstToAttribDef.get().isIdField() && firstFromAttribDef.get().isReference()){
                            fieldToName = "syncariid";
                        }
                    }
                    String fromFieldName = fieldFrom.getType() != QField.Type.DATASET ? fieldFromName.toLowerCase() : fieldFromName;
                    String joinType = j.getJoinType().getValue();
                    String toFieldName = fieldTo.getType() != QField.Type.DATASET ?  fieldToName.toLowerCase() : fieldToName;
                    String toTableName = fieldTo.getType() != QField.Type.DATASET ? getTableName(datasetIdToTableNameMap.get(datasetIdOfToField).x, connectorInfo) : datasetIdToTableNameMap.get(datasetIdOfToField).x;

                    String fromTableNameToCheck = fieldFrom.getType() != QField.Type.DATASET ? getTableName(datasetIdToTableNameMap.get(datasetIdOfFromField).x, connectorInfo) : datasetIdToTableNameMap.get(datasetIdOfFromField).x;

                    // select * from xyz a inner join abc b on
                    if (tablesNamesUsed.contains(fromTableNameToCheck+" "+fromAliasName)){
                        if (!tablesNamesUsed.contains(toTableName+" "+toAliasName )){
                            resultQuery.append(" " + joinType + " " + toTableName +" "+toAliasName + " ON CAST (" + fromAliasName + ".\"" + fromFieldName + "\" AS VARCHAR) = CAST (" + toAliasName + ".\"" + toFieldName + "\" AS VARCHAR)");
                            tablesNamesUsed.add(toTableName+" "+toAliasName);
                        }else{
                            resultQuery.append(" AND CAST (" + fromAliasName + ".\"" + fromFieldName + "\" AS VARCHAR) = CAST (" + toAliasName + ".\"" + toFieldName + "\" AS VARCHAR)");
                        }
                    }else if (tablesNamesUsed.contains(toTableName +" "+toAliasName )){
                        if (!tablesNamesUsed.contains(fromTableNameToCheck+" "+fromAliasName)){
                            resultQuery.append(" " + joinType + " " + fromTableNameToCheck +" "+fromAliasName + " ON CAST (" + fromAliasName + ".\"" + fromFieldName + "\"AS VARCHAR) = CAST (" + toAliasName + ".\"" + toFieldName + "\" AS VARCHAR)");
                            tablesNamesUsed.add(fromTableNameToCheck+" "+fromAliasName);
                        }else{
                            resultQuery.append(" AND CAST (" + fromAliasName + ".\"" + fromFieldName + "\"AS VARCHAR) = CAST (" + toAliasName + ".\"" + toFieldName + "\" AS VARCHAR)");
                        }

                    }else{
                        log.error("This condition does not make sense, one of the from should already be added in the set");
                    }

                });

            }else{
                allTableNames = StringUtils.join(tableNames, ",");
                resultQuery.append(String.format(SqlQueries.SELECT_ALL, fields, allTableNames));
            }


            // predicates
            Map<String, Object> predicate = queryConfig.getPredicate();

            if (MapUtils.isNotEmpty(predicate)){
                resultQuery.append(" WHERE ");
                PredicateParser parser = new InsightsQueryPredicateParser();
                ((InsightsQueryPredicateParser)parser).setEntityAndItsAttributeMap(datasetIdToApiNameMap);
                Expression expression = parser.fromMap(predicate);

                QueryBuilderVisitor visitor = new QueryBuilderVisitor();
                visitor.setVariableValueMap(variableValuesMapTobeused);
                visitor.setVariableDataTypeMap(((InsightsQueryPredicateParser)parser).getAliasDataTypeMap());
                visitor.setVariableLeftDataTypeMap(variableLeftDataTypes);
                expression.accept(new DynamicDispatchVisitor(visitor));
                String generatedExpression = visitor.getGeneratedBody();
                resultQuery.append(generatedExpression);
            }

            // group by
            List<AggregateConfig> groupingColumnsConfig = queryConfig.getGroupingColumns();
            if (CollectionUtils.isNotEmpty(groupingColumnsConfig)){
                List<String> groupingColumns = groupingColumnsConfig.stream().map(col -> {
                    String result = null;
                    if (null != col.getQueryFunction()){
                        QueryFunction function = col.getQueryFunction();
                        result = function.buildExpression(getEscapeChar(), fromName);
                    }else{
                        String aggregateFieldDatasetId = col.getAggregateField().getDatasetId();
                        String dataSourceAlias = col.getAggregateField().getDatasourceAlias();
                        QField.Type typ = col.getAggregateField().getType();
                        if (StringUtils.isNotEmpty(aggregateFieldDatasetId)){
                            String aliasName = StringUtils.isNotEmpty(dataSourceAlias) ? dataSourceAlias : fromName.getOrDefault(aggregateFieldDatasetId, col.getAggregateField().getName());
                            aliasName = "\"" + aliasName +"\"";
                            String nameToUse = (CollectionUtils.isNotEmpty(projectionsAlias)) && (projectionsAlias.contains(col.getAggregateField().getName()))
                                    ? col.getAggregateField().getName() : "";
                            result = (typ == QField.Type.DATASET) ? aliasName + ".\"" + col.getAggregateField().getName() + "\"" : StringUtils.isNotEmpty(nameToUse) ? "\"" + nameToUse + "\"" : aliasName + ".\"" + col.getAggregateField().getName().toLowerCase() + "\"" ;
                        }else{
                            if (typ == QField.Type.DATASET){
                                result = getDecoratedFieldName(col.getAggregateField().getName());
                            }else{
                                // Todo  Once all seeded datacard moved to new check if following is needed
                                result = (CollectionUtils.isEmpty(projectionsAlias))
                                        ? "\"" + col.getAggregateField().getName().toLowerCase() + "\"" :  (projectionsAlias.contains(col.getAggregateField().getName())) ?
                                        "\"" + col.getAggregateField().getName() + "\""  : "\"" + col.getAggregateField().getName().toLowerCase()+ "\"" ;
                            }
                        }
                    }
                    return result;
                }).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(groupingColumns)){
                    String groupingCols = StringUtils.join(groupingColumns, ",");
                    resultQuery.append(String.format(" GROUP BY %s", groupingCols));
                }
            }
            //sorting
            List<Sort> orderColumns = queryConfig.getSortList();
            List<String> builderOrderby = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(orderColumns)){
                resultQuery.append(String.format(" ORDER BY "));
                orderColumns.forEach(col -> {
                    QField sortField = col.getColumnName();
                    String sortFieldDatasetId = sortField.getDatasetId();
                    String sortFieldName = sortField.getName();

                    String sortFieldAliasName = StringUtils.isNotEmpty (sortField.getDatasourceAlias()) ? sortField.getDatasourceAlias():(MapUtils.isNotEmpty(fromName) &&(StringUtils.isNotEmpty(sortFieldDatasetId))) ? fromName.getOrDefault(sortFieldDatasetId, null) : null;
                    sortFieldAliasName = StringUtils.isNotEmpty(sortFieldAliasName) ? "\"" + sortFieldAliasName + "\"" : sortFieldAliasName;
                    if (null != sortFieldAliasName){
                        sortFieldName = sortField.getType() == QField.Type.DATASET ? sortFieldName : sortFieldName.toLowerCase();
                        builderOrderby.add(sortFieldAliasName + "." + getDecoratedFieldName(sortFieldName) + " " + (col.isAscending() ? "ASC" : "DESC"));
                    }else{
                        builderOrderby.add(getDecoratedFieldName(sortFieldName) + " " + (col.isAscending() ? "ASC" : "DESC"));
                    }
                });
                resultQuery.append(StringUtils.join(builderOrderby, ","));
            }
        }
        // limit
        if (queryConfig.getLimit() > 0) {
            resultQuery.append(" LIMIT " + queryConfig.getLimit());
        }
        if ((null != queryConfig.getOffset()) && (queryConfig.getOffset() > 0)){
            resultQuery.append(" OFFSET " + queryConfig.getOffset());
        }

        return resultQuery.toString();
    }

    // Todo: Add ths overridden method after all old views are deleted and remove tolowercase from datsatorename. But needs to be validated as this may cause other casing issues
    /*
    @Override
    protected String getCased(String name) {
        return StringUtils.isBlank(name) ? name : name.toLowerCase();
    }*/

    // Store varapiname and respective value. As for diff datasetid apiname can be same so need to store datasetid as well
    // format to store is like this datasetid_apiname -> defaultValue
    // defaultValue can be entityName.fieldName
    public void getAllVariablesValue(Set<String> datasetIds,Map<String, VariableValue> variableValuesMapTobeused){
        if (CollectionUtils.isNotEmpty(datasetIds)){
            datasetIds.forEach(id -> {
                Optional<Dataset> dataset = datasetRepo.findById(id);
                dataset.ifPresent(ds ->{
                    Map<String, Variable> variableMap = ds.getVariablesMap();
                    if (MapUtils.isNotEmpty(variableMap)){
                        Set<String> keySet = variableMap.keySet();
                        keySet.forEach(k -> {
                            Variable var = variableMap.get(k);
                            if (!variableValuesMapTobeused.containsKey(var.getApiName())){
                                variableValuesMapTobeused.put(var.getApiName(), var.getVariableValue());
                            }
                        });
                    }
                });
            });
        }
    }
    private QueryConfig buildQueryConfigFromDataset(Dataset dataset){
        assert (null != dataset);
        return dataset.getQueryConfig();
    }


    @Override
    public boolean validateQuery(String query) {
        if (StringUtils.isEmpty(query)) return  false;
        return true;
    }

}
