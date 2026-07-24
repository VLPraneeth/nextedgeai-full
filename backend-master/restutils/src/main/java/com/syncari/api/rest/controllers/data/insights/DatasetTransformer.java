package com.syncari.api.rest.controllers.data.insights;

import com.syncari.connector.data.DatastoreFieldMetadata;
import com.syncari.connector.database.DatabaseService;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Tag;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.service.*;
import com.syncari.utils.I18n;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Component
public class DatasetTransformer {

    @Autowired
    SchemaService schemaService;

    @Autowired
    TagService tagService;

    @Autowired
    UserService userService;

    @Autowired
    DatasetService service;

    @Autowired
    DatastoreService datastoreService;

    @Autowired
    ConnectorService connectorService;

    public void fillTransformedandAttributeMap(DatasetConfigDTO configDTO, Map<String, String> transformedAliasMap, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId){
        if (null != configDTO){
            // update Map and add datastore name
            List<DatasetFromDTO> datasetFromDTO = configDTO.getFromDataset();
            if (CollectionUtils.isNotEmpty(datasetFromDTO)){
                datasetFromDTO.forEach(df -> {
                    if (df.getDatasetType().name().equals(DatasourceType.ENTITY.name())){
                        Optional<EntityDefinition> entityDef = schemaService.findEntity(df.getDatasetId());
                        if (!entityDef.isPresent()){
                            entityDef = schemaService.getSyncariEntityByName(df.getApiName());
                        }
                        entityDef.ifPresent(ef-> {
                            transformedAliasMap.put(df.getDatasetId(), StringUtils.isNotEmpty(df.getAlias()) ? df.getAlias() : ef.getDataStoreName());
                            attributeDefMapForDatasetId.put(ef.getId(),ef.getAttributes());
                        });

                    }else{
                        transformedAliasMap.put(df.getDatasetId(), StringUtils.isNotEmpty(df.getAlias()) ? df.getAlias() : df.getDisplayName());

                    }
                });
            }
        }
    }


    public Dataset transformToDataset(DatasetDTO datasetDTO){
        validateCondition((connectorService.getSyncariDatastore().isPresent() && !datastoreService.isAnyDatastoreActive()), I18n.i18n("no_datastore_active"));
        Dataset dataset = transformToDatasetHelper(datasetDTO);
        DatasetConfigDTO configDTO = datasetDTO.getDatasetConfig();
        DatasetConfig config = dataset.getDatasetConfig();

        // set projections
        if (null != configDTO){
            // update Map and add datastore name
            Map<String, String> transformedAliasMap = new HashMap<>();
            Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId = new HashMap<>();
            Map<String, String> datasetIdAliasMap = new HashMap<>();
            fillTransformedandAttributeMap(configDTO,transformedAliasMap, attributeDefMapForDatasetId );

            // setting attributes and alias in two maps attributeDefMapForDatasetId and datasetIdAliasMap
            config.setFromDatasets(toDatasetListFrom(configDTO.getFromDataset(), attributeDefMapForDatasetId, datasetIdAliasMap));

            List<ProjectionDTO> projectionDTOS =  configDTO.getCalculatedFields();
            List<SelectedFieldDTO> selectedFields = configDTO.getSelectedFields();
            if (!datasetDTO.isSQLMode()){
                validateCondition((CollectionUtils.isEmpty(selectedFields) && CollectionUtils.isEmpty(projectionDTOS)), I18n.i18n("dataset_projections_empty"));
            }
            List<Projection> projections = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(projectionDTOS)){
                projectionDTOS.forEach(p -> {
                    projections.add(toProjection(p, datasetIdAliasMap, attributeDefMapForDatasetId));
                });
            }
            if (CollectionUtils.isNotEmpty(selectedFields)){
                selectedFields.forEach(p -> {
                    projections.add(toProjectionFromSelectedField(p, datasetIdAliasMap, attributeDefMapForDatasetId));
                });
            }
            config.setProjectionsList(projections);
        }
        if (null != dataset.getRawQuery()){
            dataset.setRawQuery(StringUtils.stripEnd(dataset.getRawQuery().trim(),";"));
            validateCondition(dataset.getRawQuery().matches(".*(?<!\\\\)\\\\(?!\\\\).*"),"Query contains double backslash, please use four backslash");
        }
        return dataset;
    }

    public Dataset transformToDatasetForCount(DatasetDTO datasetDTO){
        validateCondition(null == datasetDTO, "Dataset cannot be empty for this request");
        validateCondition(null == datasetDTO.getDatasetConfig(), "Dataset Configuration cannot be empty for this request");
        Dataset dataset = transformToDataset(datasetDTO);
        return transformRequestToDatasetForCount(dataset,datasetDTO.getVariablesMap());
    }

    public Dataset transformRequestToDatasetForCount(Dataset dataset,Map<String, VariableDTO> variableMapDTO){

        DatasetConfig config = dataset.getDatasetConfig();
        if (!dataset.isSQLMode()){
            config.validate();
        }
        String displayName = UUID.randomUUID().toString();
        Dataset countDataset = new Dataset().setDisplayName(displayName);

        String datasetIdFrom = dataset.getId();
        DatasetFrom datasetFrom = new DatasetFrom().setDataset(dataset).setDatasetType(DatasourceType.DATASET)
                .setApiName(dataset.getDisplayName()).setDisplayName(dataset.getDisplayName()).setDatasetId(StringUtils.isNotEmpty(datasetIdFrom) ? datasetIdFrom : "");
        DatasetConfig countDSConfig = new DatasetConfig().setFromDatasets(List.of(datasetFrom));
        Map<String, String> fromMap = new HashMap<>();
        fromMap.put("", dataset.getDisplayName());

        List<Projection> projections = config.getProjectionsList();
        QField qfield = new QField().setType(QField.Type.DATASET).setName("*");
        Projection projection = new Projection();
        QueryFunction qf = new CountQueryFunction().setColumns(List.of(qfield)).setAlias("totalCount");
        projection.setAliasName("totalCount");
        projection.setFunction(qf);
        countDSConfig.setProjectionsList(List.of(projection));
        countDSConfig.setFromDatasets(List.of(datasetFrom));
        countDSConfig.setConfigMode(dataset.getDatasetConfig().getConfigMode());
        countDataset.setDatasetConfig(countDSConfig);

        if (MapUtils.isNotEmpty(dataset.getVariablesMap())){
            countDataset.setVariablesMap(dataset.getVariablesMap());
        }
        if (MapUtils.isNotEmpty(variableMapDTO)) {
            Map<String, Variable> variableMap = toVariableMap(variableMapDTO);
            variableMap.putAll(countDataset.getVariablesMap());
            countDataset.setVariablesMap(variableMap);
        }
        if (StringUtils.isNotEmpty(dataset.getRawQuery())){
            countDataset.setRawQuery(String.format(DatabaseService.COUNT_WITH_INNERQUERY, dataset.getRawQuery().trim()));
        }
        return countDataset;
    }

    private Dataset transformToDatasetHelper(DatasetDTO datasetDTO){
        Dataset dataset = new Dataset().setName(datasetDTO.getName()).setDisplayName(datasetDTO.getDisplayName())
                .setDescription(datasetDTO.getDescription()).setVersion("V2").setSeeded(datasetDTO.isSeeded()).setDatasetType(datasetDTO.getDatasetType());
        dataset.setId(datasetDTO.getId());
        // set tags
        dataset.setTags(datasetDTO.getTags().stream()
                .map(t -> new Tag(t, true, Taggable.dataset, datasetDTO.getId()))
                .collect(Collectors.toList()));
        if (null != datasetDTO.getDraftStatus()){
            dataset.setDraftStatus(datasetDTO.getDraftStatus());
        }
        DatasetConfigDTO configDTO = datasetDTO.getDatasetConfig();
        DatasetConfig config = new DatasetConfig();

        // set projections
        if (null != configDTO){
            // update Map and add datastore name
            Map<String, String> transformedAliasMap = new HashMap<>();
            Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId = new HashMap<>();
            Map<String, String> datasetIdAliasMap = new HashMap<>();
            fillTransformedandAttributeMap(configDTO,transformedAliasMap, attributeDefMapForDatasetId );

            if (!datasetDTO.isSQLMode()){
                validateCondition(CollectionUtils.isEmpty(configDTO.getFromDataset()), I18n.i18n("dataset_from_empty"));
            }

            // setting attributes and alias in two maps attributeDefMapForDatasetId and datasetIdAliasMap
            config.setFromDatasets(toDatasetListFrom(configDTO.getFromDataset(), attributeDefMapForDatasetId, datasetIdAliasMap));

            //predicate set
            if (MapUtils.isNotEmpty(configDTO.getFilter())){
                config.setPredicate(configDTO.getFilter());
            }

            // Set Group By
            if (CollectionUtils.isNotEmpty(configDTO.getGroupBy())){
                List<GroupByDTO> fields = configDTO.getGroupBy();
                List<AggregateConfig> aggregateConfigs = new ArrayList<>();
                fields.forEach(field -> {
                    if (null != field){
                        aggregateConfigs.add(toAggregateConfig(field,attributeDefMapForDatasetId));
                    }
                });
                config.setAggregate(aggregateConfigs);
            }
            // Join elements
            if (CollectionUtils.isNotEmpty(configDTO.getJoins())){
                List<JoinDTO> joinDTOS = configDTO.getJoins();
                List<Join> joins = new LinkedList<>();
                joinDTOS.forEach(jto -> {
                    joins.add(new Join().setDatasetFieldFrom(toQField(jto.getField1(), attributeDefMapForDatasetId))
                            .setDatasetFieldTo(toQField(jto.getField2(), attributeDefMapForDatasetId))
                            .setJoinType(jto.getJoinType()));
                });
                config.setJoin(joins);
            }

            // Sorting element set
            if (CollectionUtils.isNotEmpty(configDTO.getSort())){
                List<SortDTO> sortDTOs = configDTO.getSort();
                List<Sort> softFields = new ArrayList<>();
                sortDTOs.forEach(f -> {
                    DatasetFieldDTO fieldDTO = f.field;
                    if (null != fieldDTO){
                        Sort sortField = new Sort(toQField(fieldDTO, attributeDefMapForDatasetId),f.isAscending());
                        softFields.add(sortField);
                    }
                });
                config.setOrder(softFields);
            }

            // limit set
            if (null != configDTO.getLimit()){
                if(configDTO.getLimit() <= 0){
                    throw new SyncariValidationException(String.format(i18n("dataset_incorrect_limit_error")));
                }
                config.setLimit(configDTO.getLimit());
            }
            config.setGroup(configDTO.isGroup());
            config.setConfigMode(DatasetConfig.ConfigMode.valueOf(configDTO.getConfigMode().name()));
            dataset.setDatasetConfig(config);
        }
        Map<String, VariableDTO> variableMapDTO = datasetDTO.getVariablesMap();
        if (MapUtils.isNotEmpty(variableMapDTO)) {
            Map<String, Variable> variableMap = toVariableMap(variableMapDTO);
            dataset.setVariablesMap(variableMap);
        }
        if (StringUtils.isNotEmpty(datasetDTO.getSql())){
            dataset.setRawQuery(datasetDTO.getSql());
        }
        return dataset;

    }

    private Map<String, Variable> toVariableMap(Map<String, VariableDTO> variableMapDTO){
        Map<String, Variable> variableMap = new HashMap<>();
        List<String> allVarApiNames = variableMapDTO.values().stream().map(v -> v.getApiName()).collect(Collectors.toList());
        variableMapDTO.forEach((k,v) -> {
            String apiName = v.getApiName();
            Variable variable = toVariable(v);
            if (StringUtils.isNotEmpty(apiName) && (allVarApiNames.contains(apiName))){
                if (StringUtils.isNotEmpty(v.getDisplayName())){
                    variable.setApiName(TextUtil.createApiName(v.getDisplayName()));
                }else{
                    variable.setApiName(apiName + "1");
                }
            }else{
                variable.setApiName(TextUtil.createApiName(v.getDisplayName()));
            }
            variableMap.put(k, variable);
        });
        return variableMap;
    }

    public Projection toProjection(ProjectionDTO projectionDTO, Map<String, String> entityIdWithAlias, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId){
        Projection projection = new Projection();
        AggFunctions function = projectionDTO.getAggFunctions();
        if (null == function){
            function = AggFunctions.NONE;
        }
        QueryFunction qf = function.createQueryFunction();
        List<DatasetFieldDTO> datasetFieldDTOS = projectionDTO.getDatasetFields();
        List<QField> fields = new ArrayList<>();
        // get fields datastore name for projections
        datasetFieldDTOS.forEach(df -> {
            fields.add(toQField(df,attributeDefMapForDatasetId));
        });
        function.addMoreParamsFromColumns(fields);
        qf.setColumns(fields);
        qf.setAlias(projectionDTO.getAliasName());
        qf.setDataType(projectionDTO.getDataType());
        projection.setFunction(qf);
        if (StringUtils.isNotEmpty(qf.getAlias())){
            projection.setAliasName(qf.getAlias());
        }else{
            projection.setAliasName( qf.validate()? qf.buildExpression("\"", entityIdWithAlias).replace("\"","").trim():"");
        }
        if (CollectionUtils.isNotEmpty(projectionDTO.getInnerProjections())){
            List<QueryField> fieldList = new LinkedList<>();
            projectionDTO.getInnerProjections().forEach(p -> {
                QueryField queryField = new ComplexQField();
                QueryFunction func = p.getAggFunctions().createQueryFunction();
                func.setColumns(p.getDatasetFields().stream().map(d -> toQField(d, attributeDefMapForDatasetId)).collect(Collectors.toList()));
                func.setAlias(p.getAliasName());
                func.setDataType(p.getDataType());
                queryField.setQueryFunction(func);
                fieldList.add(queryField);
            });
            ((NaryQueryFunction)qf).setInnerQueryFields(fieldList);
        }
        return projection;
    }

    public Projection toProjectionFromSelectedField(SelectedFieldDTO selectedFieldDTO, Map<String, String> entityIdWithAlias, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId){
        Projection projection = new Projection();
        AggFunctions function = AggFunctions.NONE;
        QueryFunction qf = function.createQueryFunction();
        List<QField> fields = new ArrayList<>();
        // get fields datastore name for projections'
        fields.add(toQFieldFromSelectedField(selectedFieldDTO, attributeDefMapForDatasetId));
        function.addMoreParamsFromColumns(fields);
        qf.setColumns(fields);
        if (StringUtils.isNotEmpty(selectedFieldDTO.getAlias())){
            qf.setAlias(selectedFieldDTO.getAlias());
            projection.setAliasName(selectedFieldDTO.getAlias());
        }else{
            qf.setAlias(selectedFieldDTO.getDisplayName());
            projection.setAliasName(selectedFieldDTO.getDisplayName());
        }
        qf.setDataType(selectedFieldDTO.getDataType());
        projection.setFunction(qf);

        return projection;
    }

    public QField toQFieldFromSelectedField(SelectedFieldDTO fieldDTO, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId) {
        String fieldApiName = fieldDTO.getApiName();
        String datasetId = fieldDTO.getDatasetId();
        List<AttributeDefinition> attributeDefinitionList = attributeDefMapForDatasetId.get(datasetId);
        String datastoreName = null;
        if (CollectionUtils.isNotEmpty(attributeDefinitionList)) {
            List<AttributeDefinition> requiredAttributeDefinitions = attributeDefinitionList.stream().filter(def -> def.getApiName().equalsIgnoreCase(fieldApiName)).collect(Collectors.toList());
            datastoreName = ((null != requiredAttributeDefinitions) && (CollectionUtils.isNotEmpty(requiredAttributeDefinitions))) ? requiredAttributeDefinitions.stream().findFirst().get().getDataStoreName() : fieldApiName;
        } else {
            datastoreName = fieldApiName;
        }
        return new QField().setDatasetId(fieldDTO.getDatasetId())
                .setName(datastoreName).setDataType(fieldDTO.getDataType())
                .setType(fieldDTO.getDatasetType()).setDatasourceAlias(fieldDTO.getDatasourceAlias());
    }

    private AggregateConfig toAggregateConfig(GroupByDTO groupByDTO, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId){
        AggregateConfig aggConfig = new AggregateConfig();
        QField aggField = toQField(groupByDTO.getDatasetField(), attributeDefMapForDatasetId);
        aggConfig.setAggregateField(aggField);
        String dateGroupByOption = groupByDTO.getDateGroupByOption();
        if (StringUtils.isNotEmpty(dateGroupByOption)){
            // build functionParamField based on groupByOption
            QField funcParamField = new QField();
            funcParamField.setName(dateGroupByOption).setDataType("string")
                    .setType(QField.Type.LITERAL);
            // Date Group only supports Date Part function right now
            AggFunctions aggFunction = AggFunctions.DATE_PART;
            QueryFunction func = aggFunction.createQueryFunction();
            aggFunction.addMoreParamsFromColumns(List.of(funcParamField));
            func.setColumns(List.of(funcParamField, aggField));
            aggConfig.setQueryFunction(func);
            aggConfig.setAggregateField(aggField);
        }
        return aggConfig;
    }

    public ProjectionDTO buildProjectionForTimeGrainGrouping(GroupByDTO groupByDTO){
        ProjectionDTO projectionDTO = new ProjectionDTO();
        String dateGroupByOption = groupByDTO.getDateGroupByOption();
        DatasetFieldDTO groupField = groupByDTO.getDatasetField();
        DatasetFieldDTO fieldDTO = new DatasetFieldDTO().setApiName(dateGroupByOption).setDataType("string").setDatasetType(QField.Type.LITERAL).setDisplayName(dateGroupByOption);
        projectionDTO.setDatasetFields(List.of(fieldDTO,groupField ));
        projectionDTO.setAggFunctions(AggFunctions.DATE_PART);
        String datasourceId = groupField.getDatasetId();
        QField.Type datasourceType = groupField.getDatasetType();
        String displayName;
        if (datasourceType.equals(QField.Type.ENTITY)){
            displayName = schemaService.getEntity(datasourceId).getDisplayName();
        }else if (datasourceType.equals(QField.Type.DATASET)){
            displayName = service.getDataset(datasourceId).getDisplayName();
        }else{
            throw new SyncariValidationException("Not valid type of dataset");
        }
        String generatedName = displayName + ":" + groupField.getDisplayName() + "(" + dateGroupByOption + ")";
        projectionDTO.setAliasName(generatedName);
        projectionDTO.setApiName(generatedName);
        return projectionDTO;
    }

    public QField toQField(DatasetFieldDTO fieldDTO, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId) {
        if(fieldDTO == null) return null;
        String fieldApiName = fieldDTO.getApiName();
        String datasetId = fieldDTO.getDatasetId();
        List<AttributeDefinition> attributeDefinitionList = attributeDefMapForDatasetId.get(datasetId);
        String datastoreName = null;
        if (CollectionUtils.isNotEmpty(attributeDefinitionList)) {
            List<AttributeDefinition> requiredAttributeDefinitions = attributeDefinitionList.stream().filter(def -> def.getApiName().equalsIgnoreCase(fieldApiName)).collect(Collectors.toList());
            datastoreName = ((null != requiredAttributeDefinitions) && (CollectionUtils.isNotEmpty(requiredAttributeDefinitions))) ? requiredAttributeDefinitions.stream().findFirst().get().getDataStoreName() : fieldApiName;
        } else {
            datastoreName = fieldApiName;
        }
        return new QField().setDatasetId(fieldDTO.getDatasetId())
                .setName(datastoreName).setDataType(fieldDTO.getDataType())
                .setType(fieldDTO.getDatasetType()).setDatasourceAlias(fieldDTO.getDatasourceAlias());
    }
    public List<DatasetFrom> toDatasetListFrom(List<DatasetFromDTO> fromDTO, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId, Map<String, String> datasetIdAlias){
        List<DatasetFrom> result = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(fromDTO)){
            fromDTO.forEach(dto -> result.add(toDatasetFrom(dto, attributeDefMapForDatasetId, datasetIdAlias)));
        }
        return result;
    }

    public DatasetFrom toDatasetFrom(DatasetFromDTO fromDTO){
        return toDatasetFrom(fromDTO, new HashMap<String, List<AttributeDefinition>>(), new HashMap<String, String>());
    }

    private DatasetFrom toDatasetFrom(DatasetFromDTO fromDTO, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId,Map<String, String> datasetIdAlias){
        if (null != fromDTO){
            DatasetFrom from = new DatasetFrom();
            from.setDatasetId(fromDTO.getDatasetId()).setDatasetType(fromDTO.getDatasetType())
                    .setApiName(fromDTO.getApiName()).setDisplayName(fromDTO.getDisplayName()).setAlias(fromDTO.getAlias());
            if (fromDTO.getDatasetType() == DatasourceType.ENTITY){
                Optional<EntityDefinition> entityDefinition = schemaService.findEntity(fromDTO.getDatasetId());
                entityDefinition.ifPresentOrElse(ef -> {
                    from.setDatastoreName(ef.getDataStoreName());
                    attributeDefMapForDatasetId.put(ef.getId(), ef.getAttributes());
                }, () -> {
                    throw new SyncariValidationException(String.format(i18n("entity_id_doesnot_exists"),fromDTO.getDatasetId()));
                });
            }
            datasetIdAlias.put(fromDTO.getDatasetId(), fromDTO.getApiName());
            return from;
        }
        return null;
    }

    private List<DatasetFromDTO> toDatasetDTOListFrom(List<DatasetFrom> from){
        List<DatasetFromDTO> result = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(from)){
            from.forEach(dto -> result.add(toDatasetDTOFrom(dto)));
        }
        return result;
    }

    private DatasetFromDTO toDatasetDTOFrom(DatasetFrom from){
        if (null != from){
            return new DatasetFromDTO().setDatasetId(from.getDatasetId()).setDatasetType(from.getDatasetType())
                    .setApiName(from.getApiName()).setDisplayName(from.getDisplayName()).setAlias(StringUtils.isNotEmpty(from.getAlias())? from.getAlias() : from.getDisplayName());
        }
        return null;
    }

    private DatasetFieldDTO toDatasetFieldDTO(QField qField, Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId, Map<String,String> datasetIdAliasMap){
        String datastoreName = qField.getName();
        String datasetId = qField.getDatasetId();
        List<AttributeDefinition> attributeDefinitionList = attributeDefMapForDatasetId.get(datasetId);
        String apiName = null;
        if (CollectionUtils.isNotEmpty(attributeDefinitionList)){
            List<AttributeDefinition> requiredAttributeDefinitions = attributeDefinitionList.stream().filter(def -> def.getDataStoreName().equalsIgnoreCase(datastoreName)).collect(Collectors.toList());
            apiName = ((null != requiredAttributeDefinitions) && (CollectionUtils.isNotEmpty(requiredAttributeDefinitions))) ? requiredAttributeDefinitions.stream().findFirst().get().getApiName() : datastoreName;
        }else{
            apiName = datastoreName;
        }
        return new DatasetFieldDTO().setDatasetType(qField.getType()).setDatasetId(qField.getDatasetId())
                .setDataType(qField.getDataType()).setApiName(apiName).setDatasourceAlias(StringUtils.isNotEmpty(qField.getDatasourceAlias())? qField.getDatasourceAlias() :
                        datasetIdAliasMap.getOrDefault(qField.getDatasetId(), ""));
    }
    public DatasourceDTO transformToDatasourceDTO(Dataset dataset, String datasourceAlias) {
        DatasourceDTO dto = new DatasourceDTO();
        List<DatasetFieldDTO> datasourceFields = dto.getDataSourceFields();
        DatasetConfig config = dataset.getDatasetConfig();
        if ((null != config) && (CollectionUtils.isNotEmpty(config.getProjectionsList()))){
            config.getProjectionsList().forEach(projection -> {
                DatasetFieldDTO field = new DatasetFieldDTO();
                field.setDatasetId(dataset.getId());
                field.setDatasetType(QField.Type.DATASET);
                QueryFunction qf = projection.getFunction();
                if ((null != qf) && (StringUtils.isNotEmpty(qf.getAlias()))){
                    String apiName = qf.validate()? qf.getAlias():"";
                    field.setApiName(apiName);
                    field.setDataType(qf.getDataType());
                    field.setFieldId("syncari_dataset_" + dataset.getId() + "syncari_apiname_" + apiName);
                }
                field.setDisplayName(projection.getAliasName());
                String alias = StringUtils.isNotEmpty(datasourceAlias) ? datasourceAlias : dataset.getDisplayName();
                field.setAlias( alias + ":" + projection.getAliasName());
                field.setDatasourceAlias(datasourceAlias);
                field.setDatastoreName(projection.getAliasName());
                datasourceFields.add(field);
            });

        }
        dto.setDataSourceFields(datasourceFields);
        dto.setDataSourceAlias(datasourceAlias);
        return dto;

    }

    public DatasourceDTO transformToDatasourceDTOForEntityDef(EntityDefinition entityDefinition, String datasourceAlias) {
        DatasourceDTO dto = new DatasourceDTO();
        List<AttributeDefinition> attributes = entityDefinition.getAttributes();
        List<DatasetFieldDTO> datasourceFields = dto.getDataSourceFields();
        if (CollectionUtils.isNotEmpty(attributes)){
            attributes.forEach(attrib -> {
                DatasetFieldDTO field = new DatasetFieldDTO();
                field.setDatasetId(entityDefinition.getId());
                field.setDatasetType(QField.Type.ENTITY);
                field.setApiName(attrib.getApiName());
                field.setDataType(attrib.getDataType().getName());
                field.setDisplayName(attrib.getDisplayName());
                field.setFieldId(attrib.getId());
                String alias = StringUtils.isNotEmpty(datasourceAlias) ? datasourceAlias : entityDefinition.getDisplayName();
                field.setAlias( alias + ":" + attrib.getDisplayName());
                field.setDatasourceAlias(alias);
                field.setDatastoreName(attrib.getDataStoreName());
                datasourceFields.add(field);
            });

        }
        dto.setDataSourceFields(datasourceFields);
        dto.setDataSourceAlias(datasourceAlias);
        return dto;

    }

    public DatasetDTO transformToDTO(Dataset dataset) {
        DatasetDTO dto = new DatasetDTO().setName(dataset.getName()).setDisplayName(dataset.getDisplayName())
                .setDescription(dataset.getDescription()).setSeeded(dataset.isSeeded()).setDatasetType(dataset.getDatasetType())
                .setDraftStatus(dataset.getDraftStatus()).setId(dataset.getId()).setSql(dataset.getRawQuery());
        if (StringUtils.isNotEmpty(dataset.getId())){
            dto.setTags(tagService.getTagNames(Taggable.dataset, dataset.getId()));
        }
        DatasetConfig datasetConfig = dataset.getDatasetConfig();
        DatasetConfigDTO datasetConfigDTO = new DatasetConfigDTO();
        var datasetVariablesMap = dataset.getVariablesMap();
        if (null != datasetVariablesMap && MapUtils.isNotEmpty(datasetVariablesMap)){
            Map<String, VariableDTO> variableDTOMap = new HashMap<>();
            datasetVariablesMap.forEach((k,v) -> {
                variableDTOMap.put(k, toVariableDTO(Optional.ofNullable(dataset.getId()), v));
            });
            dto.setVariablesMap(variableDTOMap);
        }
        if (null != datasetConfig) {
            Map<String, List<AttributeDefinition>> attributeDefMapForDatasetId = new HashMap<>();
            List<DatasetFrom> datasetFroms = datasetConfig.getFromDatasets();
            datasetConfigDTO.setFromDataset(toDatasetDTOListFrom(datasetFroms));
            Map<String, String> entityIdWithAliasMap = new HashMap<>();
            datasetFroms.forEach(datasetFrom -> {
                String dsId = datasetFrom.getDatasetId();
                if (StringUtils.isNotEmpty(dsId)){
                    entityIdWithAliasMap.put(dsId, StringUtils.isNotEmpty(datasetFrom.getAlias())? datasetFrom.getAlias() : datasetFrom.getDisplayName());
                    if (datasetFrom.getDatasetType().name().equals(DatasourceType.ENTITY.name())){
                        Optional<EntityDefinition> entityDef = schemaService.findEntity(dsId);
                        if (!entityDef.isPresent()){
                            entityDef = schemaService.getSyncariEntityByName(datasetFrom.getApiName());
                        }
                        entityDef.ifPresent(ef-> {
                            attributeDefMapForDatasetId.put(ef.getId(), ef.getAttributes());
                        });
                    }
                }
            });

            List<ProjectionDTO> projectionDTOList = new ArrayList<>();
            List<SelectedFieldDTO> selectedFieldDTOList = new ArrayList<>();
            datasetConfig.getProjectionsList().forEach(p -> {
                if (null != p.getFunction()){
                    if (p.getFunction() instanceof NoQueryFunction){
                        SelectedFieldDTO selectedFieldDTO = new SelectedFieldDTO();
                        selectedFieldDTO.setAlias(p.getAliasName());
                        selectedFieldDTO.setDisplayName(p.getAliasName());
                        if (CollectionUtils.isNotEmpty(p.getFunction().getColumns())){
                            selectedFieldDTO.setDataType(p.getFunction().getColumns().stream().findFirst().get().getDataType());
                            selectedFieldDTO.setDatasetId(p.getFunction().getColumns().stream().findFirst().get().getDatasetId());
                            selectedFieldDTO.setDatasetType(p.getFunction().getColumns().stream().findFirst().get().getType());
                            selectedFieldDTO.setApiName(p.getFunction().getColumns().stream().findFirst().get().getName());
                            String datasourceAlias = p.getFunction().getColumns().stream().findFirst().get().getDatasourceAlias();
                            selectedFieldDTO.setDatasourceAlias(StringUtils.isNotEmpty(datasourceAlias) ? datasourceAlias :
                                    entityIdWithAliasMap.get(p.getFunction().getColumns().stream().findFirst().get().getDatasetId()));
                            selectedFieldDTOList.add(selectedFieldDTO);
                        }
                    }else{
                        ProjectionDTO projectionDTO = new ProjectionDTO();
                        projectionDTO.setAliasName(p.getAliasName());
                        projectionDTO.setAggFunctions(p.getFunction().getQueryFunction());
                        projectionDTO.setDataType(p.getFunction().getDataType());
                        List<DatasetFieldDTO> datasetFieldDTOList = new ArrayList<>();
                        if (CollectionUtils.isNotEmpty(p.getFunction().getColumns())){
                            p.getFunction().getColumns().forEach(f -> {
                                datasetFieldDTOList.add(toDatasetFieldDTO(f,attributeDefMapForDatasetId,entityIdWithAliasMap));
                            });
                        }
                        List<ProjectionDTO> innerProjs = new LinkedList();
                        if (p.getFunction() instanceof NaryQueryFunction &&  CollectionUtils.isNotEmpty(((NaryQueryFunction)p.getFunction()).getInnerQueryFields())){
                            ((NaryQueryFunction)p.getFunction()).getInnerQueryFields().forEach(fi -> {
                                ProjectionDTO innerp = new ProjectionDTO();
                                innerp.setAggFunctions(fi.getFunction());
                                innerp.setAliasName(fi.getAlias());
                                innerp.setDataType(fi.getFunction().getDataType());
                                List<DatasetFieldDTO> datasetFieldDTOListInner = new ArrayList<>();

                                fi.getQueryFunction().getColumns().forEach(i -> {
                                    datasetFieldDTOListInner.add(toDatasetFieldDTO(i,attributeDefMapForDatasetId,entityIdWithAliasMap));
                                });
                                innerp.setDatasetFields(datasetFieldDTOListInner);
                                innerp.setApiName(fi.getName());
                                // Add required fields and then fetch this to build innerfield while transformation
                                innerProjs.add(innerp);
                            });
                        }
                        projectionDTO.setInnerProjections(innerProjs);
                        projectionDTO.setDatasetFields(datasetFieldDTOList);
                        projectionDTO.setApiName(p.getFunction().validate() ? p.getFunction().buildExpression("\"", entityIdWithAliasMap) : "");
                        projectionDTOList.add(projectionDTO);
                    }
                }
            });
            datasetConfigDTO.setCalculatedFields(projectionDTOList);
            datasetConfigDTO.setSelectedFields(selectedFieldDTOList);
            datasetConfigDTO.setFilter(datasetConfig.getPredicate());
            // group by
            List<GroupByDTO> groupByList = new ArrayList<>();
            datasetConfig.getAggregate().forEach(agg -> {
                QField aggField = agg.getAggregateField();
                QueryFunction qf = agg.getQueryFunction();
                String dateGroupByOption = null;
                if (qf instanceof DatePartQueryFunction){
                    dateGroupByOption = ((DatePartQueryFunction)qf).getDatePartField();
                }
                if (null != aggField) {
                    groupByList.add(new GroupByDTO().setDatasetField(toDatasetFieldDTO(aggField, attributeDefMapForDatasetId,entityIdWithAliasMap)).setDateGroupByOption(dateGroupByOption));
                }
            });
            datasetConfigDTO.setGroupBy(groupByList);
            datasetConfigDTO.setConfigMode(DatasetConfigDTO.ConfigMode.valueOf(datasetConfig.getConfigMode().name()));

            // Joins
            List<JoinDTO> joinDTOs = new ArrayList<>();
            datasetConfig.getJoin().forEach(j -> {
                if (j.getDatasetFieldFrom() != null && j.getDatasetFieldTo() != null) {
                    JoinDTO joinDTO = new JoinDTO();
                    joinDTO.setJoinType(j.getJoinType());
                    joinDTO.setField1(toDatasetFieldDTO(j.getDatasetFieldFrom(), attributeDefMapForDatasetId, entityIdWithAliasMap));
                    joinDTO.setField2(toDatasetFieldDTO(j.getDatasetFieldTo(), attributeDefMapForDatasetId, entityIdWithAliasMap));
                    joinDTOs.add(joinDTO);
                }
            });
            datasetConfigDTO.setJoins(joinDTOs);
            // Sort
            List<SortDTO> sortDTOs = new ArrayList<>();
            datasetConfig.getOrder().forEach(s -> {
                SortDTO sortDTO = new SortDTO();
                sortDTO.setAscending(s.isAscending());
                sortDTO.setField(toDatasetFieldDTO(s.getColumnName(), attributeDefMapForDatasetId,entityIdWithAliasMap));
                sortDTOs.add(sortDTO);
            });
            datasetConfigDTO.setSort(sortDTOs);
            if (datasetConfig.getLimit() > 0) {
                datasetConfigDTO.setLimit(datasetConfig.getLimit());
            }
            datasetConfigDTO.setGroup(datasetConfig.isGroup());
            dto.setDatasetConfig(datasetConfigDTO);

        }

        if(!StringUtils.isBlank(dataset.getCreatedBy())){
            userService.findUserById(dataset.getCreatedBy()).ifPresent(u -> dto.setCreatedBy(u.getName()));
        }
        if(!StringUtils.isBlank(dataset.getUpdatedBy())){
            userService.findUserById(dataset.getUpdatedBy()).ifPresent(u -> dto.setUpdatedBy(u.getName()));
        }

        if(dataset.getCreatedAt() != null){
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(dataset.getCreatedAt().toInstant(), ZoneOffset.UTC);
            dto.setCreatedAt(dateTime);
        }
        if(dataset.getUpdatedAt() != null){
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(dataset.getUpdatedAt().toInstant(), ZoneOffset.UTC);
            dto.setUpdatedAt(dateTime);
        }

        return dto;
    }

    public DatasetFunctionDTO toFunctionDTO(AggFunctions f) {
        return new DatasetFunctionDTO()
                .setName(f.name())
                .setDisplayName(f.getDisplayName())
                .setDataType(f.getDataType())
                .setDescription(f.getDescription()).setAggregate(f.isAggregate()).setFunctionInputDataTypes(f.getInputDataTypes());
    }

    public DatasetExportJobDTO datasetExportJobDTO(DatasetExport datasetExport) {
        DatasetExportJobDTO result =  new DatasetExportJobDTO()
                .setExpiredTime(datasetExport.getExpiredTime())
                .setRequestedTime(datasetExport.getRequestedTime())
                .setStatus(datasetExport.getStatus().name())
                .setNumberOfRecords(datasetExport.getNumberOfRecords())
                .setExportJobId(datasetExport.getId()).setUserName(datasetExport.getUserName());
        result.setExpiryStatus(Instant.now().minusMillis(datasetExport.getExpiredTime().toEpochMilli()).toEpochMilli() > 0);
        return result;
    }


    public DatasetGroupByTimeGrainOptionsDTO toGroupByOptionDTO(DateGroupByOption f) {
        return  new DatasetGroupByTimeGrainOptionsDTO().setName(f.getValue())
                .setDisplayName(f.getDisplayName())
                .setDescription(f.getDescription());
    }

    public List<DatasetSampleColumnsDTO> toDatasetSampleColumnsDTOS(List<Projection> projections, Map<String, String> aliasMap) {
        return projections.stream().map(p ->{
            DatasetSampleColumnsDTO dto = new DatasetSampleColumnsDTO();
            validateCondition(!p.getFunction().validate(), I18n.i18n("projection_aggfunction_wrongfields"));
            dto.setApiName(p.getFunction().buildExpression("\"", aliasMap));
            dto.setDisplayName(p.getAliasName());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<DatasetSampleColumnsDTO> toDatasetSampleColumnsDTOSFromDatastoreMetadata(List<DatastoreFieldMetadata> columns) {
        return columns.stream().map(e -> {
            DatasetSampleColumnsDTO dto = new DatasetSampleColumnsDTO();
            dto.setApiName(e.getFieldExpression());
            dto.setDisplayName(e.getAliasName());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<List<DatasetSampleDataDTO>> toDatasetSampleDataDTOS(List<Map<String, Object>> pairsList){
        return pairsList.stream().map(insideMap -> insideMap.entrySet().stream().map(p -> {
            DatasetSampleDataDTO dto = new DatasetSampleDataDTO();
            dto.setColumnDisplayName(p.getKey());
            dto.setValue(p.getValue());
            return dto;
        }).collect(Collectors.toList())).collect(Collectors.toList());
    }

    public List<VariableDTO> transformVariables(String datasetId,List<Variable> allVars){
        if ((StringUtils.isEmpty(datasetId)) || CollectionUtils.isEmpty(allVars)){
            return null;
        }
        List<VariableDTO> allVarDTOs = new ArrayList<>();
        allVars.forEach(var -> {
            allVarDTOs.add(toVariableDTO(Optional.ofNullable(datasetId), var));
        });
        return allVarDTOs;
    }

    public Variable toVariable(VariableDTO variabledto){
        if ((null == variabledto)){
            return null;
        }
        return new Variable().setDatatype(variabledto.getDatatype()).setApiName(variabledto.getApiName())
                .setDisplayName(variabledto.getDisplayName()).setHelpText(variabledto.getHelpText()).setRequired(variabledto.isRequired())
                .setUpdatable(variabledto.isUpdatable()).setVariableValue(toVariableValue(variabledto.getVariableDefaultValue())).setMultiValueField(variabledto.isMultiValueField());
    }

    public VariableDTO toVariableDTO(Optional<String> datasetId,Variable variable){
        if (null == variable){
            return null;
        }
        return new VariableDTO().setDatasetId(datasetId.isPresent() ? datasetId.get() : null).setDatatype(variable.getDatatype()).setApiName(variable.getApiName())
                .setDisplayName(variable.getDisplayName()).setHelpText(variable.getHelpText()).setRequired(variable.isRequired())
                .setUpdatable(variable.isUpdatable()).setVariableDefaultValue(toVariableValueDTO(variable.getVariableValue())).setMultiValueField(variable.isMultiValueField());
    }

    public VariableValueDTO toVariableValueDTO(VariableValue variableValue){
        if ((null == variableValue)){
            return null;
        }
        return new VariableValueDTO().setDatasetId(variableValue.getDatasetId()).setDatasetName(variableValue.getDatasetName()).setAdditionalParamForDefaultVal(variableValue.getAdditionalParamForDefaultVal())
                .setDatatype(variableValue.getDatatype()).setDefaultValue(variableValue.getDefaultValue()).setDefaultValueType(variableValue.getDefaultValueType());

}

    public VariableValue toVariableValue(VariableValueDTO variableValue){
        if ((null == variableValue)){
            return null;
        }
        VariableValue value = new VariableValue().setDatasetId(variableValue.getDatasetId()).setDatasetName(variableValue.getDatasetName()).setAdditionalParamForDefaultVal(variableValue.getAdditionalParamForDefaultVal())
                .setDatatype(variableValue.getDatatype()).setDefaultValue(variableValue.getDefaultValue()).setDefaultValueType(variableValue.getDefaultValueType());

        return value;
    }

    public JoinDTO toJoinDTO(Join join){
        JoinDTO joinDTO = new JoinDTO();
        joinDTO.setJoinType(join.getJoinType());
        QField from = join.getDatasetFieldFrom();
        QField to = join.getDatasetFieldTo();

        joinDTO.setField1(new DatasetFieldDTO().setDatasetType(from.getType()).setDatasetId(from.getDatasetId())
                .setDataType(from.getDataType()).setApiName(from.getName()).setDatasetType(QField.Type.ENTITY)
                .setDisplayName(from.getName()).setDatasourceAlias(from.getDatasourceAlias()));
        joinDTO.setField2(new DatasetFieldDTO().setDatasetType(to.getType()).setDatasetId(to.getDatasetId())
                .setDataType(to.getDataType()).setApiName(to.getName()).setDatasetType(QField.Type.ENTITY)
                .setDisplayName(to.getName()).setDatasourceAlias(to.getDatasourceAlias()));

        // set id for fields
        Optional<EntityDefinition> fromEntity = schemaService.findEntity(from.getDatasetId());
        Optional<EntityDefinition> toEntity = schemaService.findEntity(to.getDatasetId());
        fromEntity.ifPresent(e -> {
            e.getField(from.getName()).ifPresent(f -> joinDTO.getField1().setFieldId(f.getId()));
        });

        toEntity.ifPresent(e -> {
            e.getField(to.getName()).ifPresent(f -> joinDTO.getField2().setFieldId(f.getId()));
        });
        return joinDTO;
    }
}
