package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.mongodb.client.MongoCollection;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.insights.DatasourceType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.*;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.service.DatacardService;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Slf4j
public class UpdateInsightsDatasetAndDatacardToV2 {

    private final DatasetService datasetService = MigrationContext.getDatasetService();
    private final DatacardService datacardService = MigrationContext.getDatacardService();
    private final SchemaService schemaService = MigrationContext.getSchemaService();

    @ChangeSet(order = "001", id = "quarterlyClosedPipelineRevenueDS", author = "rohit", runAlways = true)
    public void quarterlyClosedPipelineRevenueDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("quarterlyClosedPipelineRevenueDS").setDisplayName("Sales by Quarter").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            assert (entityDefOpt.isPresent());
            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            config.setFromDatasets(List.of(new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY)));

            QueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                    .setAlias("Amount in USD").setDataType("integer");

            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Amount in USD");

            QueryFunction concatQueryFunction = new ConcatQueryFunction();
            concatQueryFunction.setAlias("Quarter").setDataType("text")
                    .setColumns(List.of(
                            new QField("Q", QField.Type.LITERAL),
                            new QField("fiscalquarter", QField.Type.COLUMN),
                            new QField(" ", QField.Type.LITERAL),
                            new QField("fiscalyear", QField.Type.COLUMN)
                    ));

            Projection concatProjection = new Projection();
            concatProjection.setFunction(concatQueryFunction);
            concatProjection.setAliasName("Quarter");

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                    .setAlias("closedate").setDataType("date");

            Projection closeDate = new Projection();
            closeDate.setFunction(closedateNoQueryFunction);
            closeDate.setAliasName("closedate");

            config.setProjectionsList(List.of(sumProjection, concatProjection));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))));
            config.setOrder((List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true))));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", false)
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("StageName")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "Closed Won")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("CloseDate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "{{thisyear}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable var = new Variable().setApiName("thisyear").setDisplayName("this year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("thisyear",var));
            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for quarterlyClosedPipelineRevenueDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("quarterlyClosedPipelineRevenueDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "002", id = "quarterlyClosedPipelineRevenueDC", author = "rohit", runAlways = true)
    public void quarterlyClosedPipelineRevenueDC(MongoTemplate template){
        try{
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "quarterlyClosedPipelineRevenueDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("revenueBar").setDatasetId(datasetId);
            Variable var = new Variable().setApiName("var1").setDisplayName("var1").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            vizConfig.setVariablesMap(Map.of("var1", var));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("quarterlyClosedPipelineRevenueDC").setConfig(vizConfig).setType(VizType.BAR).setDisplayName("Sales by Quarter");
            Datacard datacard = new Datacard().setName("quarterlyClosedPipelineRevenueDC")
                    .setDisplayName("Sales by Quarter")
                    .setDescription("Amount in $ of closed won opportunities by quarter")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard quarterlyClosedPipelineRevenueDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "003", id = "yearlyClosedPipelineRevenueDS", author = "rohit", runAlways = true)
    public void yearlyClosedPipelineRevenueDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("yearlyClosedPipelineRevenueDS").setDisplayName("Current Year Sales").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            assert (entityDefOpt.isPresent());
            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            config.setFromDatasets(List.of(new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY)));


            SumQueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                    .setAlias("Total").setDataType("integer");
            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Amount in USD");

            config.setProjectionsList(List.of(sumProjection));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("datatype", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", false)
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("StageName")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "Closed Won")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("FiscalYear")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "integer", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "{{currentyear}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Integer currentYear = Calendar.getInstance().get(Calendar.YEAR);
            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            Variable var = new Variable().setApiName("currentyear").setDisplayName("currentyear").setDatatype("integer").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue(currentYear).setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("integer"));

            dataset.setVariablesMap(Map.of("currentyear",var));
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for yearlyClosedPipelineRevenueDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("yearlyClosedPipelineRevenueDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "004", id = "annualRecurringRevenueDC", author = "rohit", runAlways = true)
    public void annualRecurringRevenueDC(MongoTemplate template){
        try{
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "yearlyClosedPipelineRevenueDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("arr").setDatasetId(datasetId);

            Integer currentYear = Calendar.getInstance().get(Calendar.YEAR);
            Variable var = new Variable().setApiName("currentyear").setDisplayName("currentyear").setDatatype("integer").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue(currentYear).setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("integer"));

            vizConfig.setVariablesMap(Map.of("currentyear",var));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("annualRecurringRevenueDC").setConfig(vizConfig).setType(VizType.BAR).setDisplayName("Current Year Sales");
            Datacard datacard = new Datacard().setName("annualRecurringRevenueDC")
                    .setDisplayName("Current Year Sales")
                    .setDescription("Amount in $ of closed won opportunities this year")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard annualRecurringRevenueDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "005", id = "nextFewQuaterOpenPipelinesDS", author = "rohit", runAlways = true)
    public void nextFewQuaterOpenPipelinesDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("nextFewQuaterOpenPipelinesDS").setDisplayName("Pipeline By Close Date").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            //account
            Optional<EntityDefinition>  syncariAccountEntity = schemaService.findEntity(syncariConnectorId, "account");

            assert (entityDefOpt.isPresent());
            assert (syncariAccountEntity.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);
            DatasetFrom actDataset = new DatasetFrom().setDatasetId(syncariAccountEntity.get().getId()).setDisplayName(syncariAccountEntity.get().getDisplayName()).
                    setDatastoreName(syncariAccountEntity.get().getDataStoreName()).setApiName(syncariAccountEntity.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset,actDataset));

            QField opptyjoinField = new QField().setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY).setName("AccountId").setDataType("reference");
            QField accountjoinField = new QField().setDatasetId(syncariAccountEntity.get().getId()).setType(QField.Type.ENTITY).setName("syncariid").setDataType("string");

            config.setJoin(List.of(new Join().setJoinType(JoinType.Inner).setDatasetFieldFrom(opptyjoinField).setDatasetFieldTo(accountjoinField)));


            SumQueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                    .setAlias("Amount").setDataType("integer");

            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Amount in USD");

            ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
            concatQueryFunction.setAlias("Quarter").setDataType("text")
                    .setColumns(List.of(
                            new QField("Q", QField.Type.LITERAL),
                            new QField("fiscalquarter", QField.Type.COLUMN),
                            new QField(" ", QField.Type.LITERAL),
                            new QField("fiscalyear", QField.Type.COLUMN)
                    ));

            Projection concatProjection = new Projection();
            concatProjection.setFunction(concatQueryFunction);
            concatProjection.setAliasName("Quarter");

            config.setProjectionsList(List.of(concatProjection,sumProjection));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))));
            config.setOrder(List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true)));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", true)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gte",
                                "right", Map.of("type", "literal", "value", "{{last0days}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "lt",
                                "right", Map.of("type", "literal", "value", "{{next1year}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            Variable next1year = new Variable().setApiName("next1year").setDisplayName("next1year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 1 year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            dataset.setVariablesMap(Map.of("last0days",last0days,"next1year",next1year));

            Map<String, Object> deletedPredicate = new HashMap<>();
            deletedPredicate.put("operator", "or");
            List<Map<String, Object>> deletedPredicates = new ArrayList<>();
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });

            deletedPredicate.put("predicates", deletedPredicates);
            deletedPredicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(deletedPredicate);

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for nextFewQuaterOpenPipelinesDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("nextFewQuaterOpenPipelinesDS  dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }
    @ChangeSet(order = "006", id = "nextFewQuaterOpenPipelinesDC", author = "rohit", runAlways = true)
    public void nextFewQuaterOpenPipelinesDC(MongoTemplate template){
        try{
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "nextFewQuaterOpenPipelinesDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("nextFewQuaterOpenPipelinesDC").setDatasetId(datasetId);
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            Variable next1year = new Variable().setApiName("next1year").setDisplayName("next1year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 1 year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            vizConfig.setVariablesMap(Map.of("last0days",last0days,"next1year",next1year));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("nextFewQuaterOpenPipelinesDC").setConfig(vizConfig).setType(VizType.BAR).setDisplayName("Pipeline By Close Date");
            Datacard datacard = new Datacard().setName("nextFewQuaterOpenPipelinesDC")
                    .setDisplayName("Pipeline By Close Date")
                    .setDescription("Pipeline By Close Date")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard nextFewQuaterOpenPipelinesDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "007", id = "allOpenPipelineByTypeDS", author = "rohit", runAlways = true)
    public void allOpenPipelineByTypeDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("allOpenPipelineByTypeDS").setDisplayName("All Open Pipeline").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");

            assert (entityDefOpt.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset));

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                    .setAlias("Close Date").setDataType("date");

            Projection closedate = new Projection();
            closedate.setFunction(closedateNoQueryFunction);
            closedate.setAliasName("Close Date");

            ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
            concatQueryFunction.setAlias("Quarter").setDataType("text")
                    .setColumns(List.of(
                            new QField("fiscalyear", QField.Type.COLUMN),
                            new QField(" ", QField.Type.LITERAL),
                            new QField("Q", QField.Type.LITERAL),
                            new QField("fiscalquarter", QField.Type.COLUMN)
                    ));

            Projection concatProjection = new Projection();
            concatProjection.setFunction(concatQueryFunction);
            concatProjection.setAliasName("Quarter");

            NoQueryFunction amtNoQueryFunction = new NoQueryFunction();
            amtNoQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                    .setAlias("Amount").setDataType("currency");

            Projection amtProjection = new Projection();
            amtProjection.setFunction(amtNoQueryFunction);
            amtProjection.setAliasName("Amount");

            NoQueryFunction opptyTypeNoQueryFunction = new NoQueryFunction();
            opptyTypeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                    .setAlias("Opportunity Type").setDataType("text");

            Projection typeProjection = new Projection();
            typeProjection.setFunction(opptyTypeNoQueryFunction);
            typeProjection.setAliasName("Opportunity Type");

            NoQueryFunction stageNameNoQueryFunction = new NoQueryFunction();
            stageNameNoQueryFunction.setColumns(List.of(new QField("stagename", QField.Type.COLUMN)))
                    .setAlias("Stage Name").setDataType("text");

            Projection stageProjection = new Projection();
            stageProjection.setFunction(stageNameNoQueryFunction);
            stageProjection.setAliasName("Stage Name");


            config.setProjectionsList(List.of(concatProjection,closedate,amtProjection,typeProjection,stageProjection));
            config.setOrder(List.of(new Sort(new QField().setName(concatProjection.getAliasName()), true), new Sort(new QField().setName(typeProjection.getAliasName()), true),
                    new Sort(new QField().setName(stageProjection.getAliasName()), true)));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", true)
                        );
                        predicates.add(map);
                    }

                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gt",
                                "right", Map.of("type", "literal", "value", "{{last0days}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "lte",
                                "right", Map.of("type", "literal", "value", "{{next1year}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable next1year = new Variable().setApiName("next1year").setDisplayName("next1year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 1 year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("last0days",last0days,"next1year",next1year));

            Map<String, Object> deletedPredicate = new HashMap<>();
            deletedPredicate.put("operator", "or");
            List<Map<String, Object>> deletedPredicates = new ArrayList<>();
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });

            deletedPredicate.put("predicates", deletedPredicates);
            deletedPredicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(deletedPredicate);

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for allOpenPipelineByTypeDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("allOpenPipelineByTypeDS  dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "008", id = "allOpenPipelineByTypeDC", author = "rohit", runAlways = true)
    public void allOpenPipelineByTypeDC(MongoTemplate template){
        try{
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "allOpenPipelineByTypeDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("allOpenPipelineByTypeDC").setDatasetId(datasetId);

            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable next1year = new Variable().setApiName("next1year").setDisplayName("next1year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 1 year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("last0days",last0days,"next1year",next1year));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("allOpenPipelineByTypeDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("All Open Pipeline");
            Datacard datacard = new Datacard().setName("allOpenPipelineByTypeDC")
                    .setDisplayName("All Open Pipeline")
                    .setDescription("All Open Pipeline ordered by type for current and next 3 quarters")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard allOpenPipelineByTypeDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "009", id = "salesFunnelDS", author = "rohit", runAlways = true)
    public void salesFunnelDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("salesFunnelDS").setDisplayName("Sales Funnel By Stage").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");

            assert (entityDefOpt.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset));

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("closedate").setDataType("date");

            Projection closedate = new Projection();
            closedate.setFunction(closedateNoQueryFunction);
            closedate.setAliasName("closedate");

            SumQueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("Total").setDataType("integer");
            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Amount");

            CountQueryFunction countQueryFunction = new CountQueryFunction();
            countQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("Oppty Count").setDataType("date");

            Projection countProjection = new Projection();
            countProjection.setFunction(countQueryFunction);
            countProjection.setAliasName("Oppty Count");

            NoQueryFunction stageNameNoQueryFunction = new NoQueryFunction();
            stageNameNoQueryFunction.setColumns(List.of(new QField("stagename", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("Stage Name").setDataType("text");

            Projection stageProjection = new Projection();
            stageProjection.setFunction(stageNameNoQueryFunction);
            stageProjection.setAliasName("Stage Name");


            config.setProjectionsList(List.of(countProjection,stageProjection));
            config.setOrder(List.of(new Sort(new QField().setName(stageProjection.getAliasName()), true)));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName(stageProjection.getAliasName())
                    .setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY))));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", true)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gt",
                                "right", Map.of("type", "literal", "value", "{{last0days}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "lte",
                                "right", Map.of("type", "literal", "value","{{next1year}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable next1year = new Variable().setApiName("next1year").setDisplayName("next1year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 1 year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("last0days",last0days,"next1year",next1year));

            Map<String, Object> deletedPredicate = new HashMap<>();
            deletedPredicate.put("operator", "or");
            List<Map<String, Object>> deletedPredicates = new ArrayList<>();
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });

            deletedPredicate.put("predicates", deletedPredicates);
            deletedPredicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(deletedPredicate);

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for salesFunnelDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("salesFunnelDS  dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "010", id = "salesFunnelDC", author = "rohit", runAlways = true)
    public void salesFunnelDC(MongoTemplate template){
        try{
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "salesFunnelDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("salesFunnelDC").setDatasetId(datasetId);
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable next1year = new Variable().setApiName("next1year").setDisplayName("next1year").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 1 year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("last0days",last0days,"next1year",next1year));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("salesFunnelDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Sales Funnel");
            Datacard datacard = new Datacard().setName("salesFunnelDC")
                    .setDisplayName("Sales Funnel")
                    .setDescription("Sales Funnel By Stage for current and next 3 quarters")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard salesFunnelDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "011", id = "openRenewalLogoCountDS", author = "rohit", runAlways = true)
    public void openRenewalLogoCountDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("openRenewalLogoCountDS").setDisplayName("Renewal Logo Count").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");

            assert (entityDefOpt.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset));

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                    .setAlias("closedate").setDataType("date");

            Projection closedate = new Projection();
            closedate.setFunction(closedateNoQueryFunction);
            closedate.setAliasName("closedate");

            DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
            CountQueryFunction countQueryFunction = new CountQueryFunction();

            distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                    .setDataType("text");
            countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
            countQueryFunction.setAlias("Open Renewal Logo Count").setDataType("integer")
                    .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));

            Projection countProjection = new Projection();
            countProjection.setFunction(countQueryFunction);
            countProjection.setAliasName("Open Renewal Logo Count");


            config.setProjectionsList(List.of(countProjection));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", true)
                        );
                        predicates.add(map);
                    }

                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gte",
                                "right", Map.of("type", "literal", "value", "{{thisyear}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("type")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value","Renewal")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable thisyear = new Variable().setApiName("thisyear").setDisplayName("thisyear").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("thisyear",thisyear));


            Map<String, Object> deletedPredicate = new HashMap<>();
            deletedPredicate.put("operator", "or");
            List<Map<String, Object>> deletedPredicates = new ArrayList<>();
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });

            deletedPredicate.put("predicates", deletedPredicates);
            deletedPredicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(deletedPredicate);

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for openRenewalLogoCountSeedDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("openRenewalLogoCountSeedDS  dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "012", id = "openRenewalLogoCountDC", author = "rohit", runAlways = true)
    public void openRenewalLogoCountDC(MongoTemplate template){


        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "openRenewalLogoCountDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("openRenewalLogoCountDC").setDatasetId(datasetId);
            Variable thisyear = new Variable().setApiName("thisyear").setDisplayName("thisyear").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("thisyear",thisyear));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("openRenewalLogoCountDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Renewal Logo Count");
            Datacard datacard = new Datacard().setName("openRenewalLogoCountDC")
                    .setDisplayName("Renewal Logo Count")
                    .setDescription("Customers with upcoming renewal opportunities")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard openRenewalLogoCountDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }


    @ChangeSet(order = "013", id = "upcomingRenewalDatesDS", author = "rohit", runAlways = true)
    public void upcomingRenewalDatesDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("upcomingRenewalDatesDS").setDisplayName("Upcoming Renewal Dates").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");

            assert (entityDefOpt.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset));

            NoQueryFunction nameNoQueryFunction = new NoQueryFunction();
            nameNoQueryFunction.setColumns(List.of(new QField("name", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("Opportunity Name").setDataType("text");
            Projection name = new Projection();
            name.setFunction(nameNoQueryFunction);
            name.setAliasName("Opportunity Name");

            NoQueryFunction amountNoQueryFunction = new NoQueryFunction();
            amountNoQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                    .setAlias("Open Pipeline Amount").setDataType("integer");

            Projection amt = new Projection();
            amt.setFunction(amountNoQueryFunction);
            amt.setAliasName("Open Pipeline Amount");

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                    .setAlias("Close Date").setDataType("date");
            Projection closedate = new Projection();
            closedate.setFunction(closedateNoQueryFunction);
            closedate.setAliasName("Close Date");

            config.setProjectionsList(List.of(name, amt, closedate));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", true)
                        );
                        predicates.add(map);
                    }

                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gt",
                                "right", Map.of("type", "literal", "value", "{{last0days}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "lte",
                                "right", Map.of("type", "literal", "value","{{next6months}}")
                        );
                        predicates.add(cd);
                    }

                    if (att.getApiName().equalsIgnoreCase("type")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value","Renewal")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable next6months = new Variable().setApiName("next6months").setDisplayName("next6months").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 6 months").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("currentyear",last0days,"next6months",next6months));

            Map<String, Object> deletedPredicate = new HashMap<>();
            deletedPredicate.put("operator", "or");
            List<Map<String, Object>> deletedPredicates = new ArrayList<>();
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });

            deletedPredicate.put("predicates", deletedPredicates);
            deletedPredicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(deletedPredicate);

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            config.setOrder(List.of(new Sort(new QField().setName(amt.getAliasName()), false)));
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for upcomingRenewalDatesDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("upcomingRenewalDatesDS  dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "014", id = "upcomingRenewalDatesDC", author = "rohit", runAlways = true)
    public void upcomingRenewalDatesDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "upcomingRenewalDatesDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("upcomingRenewalDatesDC").setDatasetId(datasetId);
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable next6months = new Variable().setApiName("next6months").setDisplayName("next6months").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("next 6 months").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("currentyear",last0days,"next6months",next6months));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("upcomingRenewalDatesDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Upcoming Renewal Dates");
            Datacard datacard = new Datacard().setName("upcomingRenewalDatesDC")
                    .setDisplayName("Upcoming Renewal Dates")
                    .setDescription("Upcoming renewal opportunity dates")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard upcomingRenewalDatesDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }
    @ChangeSet(order = "015", id = "openRenewalsDS", author = "rohit", runAlways = true)
    public void openRenewalsDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("openRenewalsDS").setDisplayName("Open Renewal").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();

            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");

            assert (entityDefOpt.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset));

            DateTruncQueryFunction dateTruncQueryFunction = new DateTruncQueryFunction();
            dateTruncQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                    .setAlias("Close Date Month").setDataType("text");
            dateTruncQueryFunction.setTruncatedField("month");

            Projection closedatemonth = new Projection();
            closedatemonth.setFunction(dateTruncQueryFunction);
            closedatemonth.setAliasName("Close Date Month");

            ToCharQueryFunction toCharQueryFunction = new ToCharQueryFunction();
            toCharQueryFunction.setColumns(List.of(new QField("closedate", QField.Type.COLUMN)))
                    .setAlias("Close Month").setDataType("text");
            toCharQueryFunction.setToCharField("Mon");

            Projection charMonth = new Projection();
            charMonth.setFunction(toCharQueryFunction);
            charMonth.setAliasName("Close Month");

            DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
            CountQueryFunction countQueryFunction = new CountQueryFunction();

            distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                    .setDataType("text");
            countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
            countQueryFunction.setAlias("Open Renewal Logo Count").setDataType("integer")
                    .setColumns(List.of(new QField("accountid", QField.Type.COLUMN)));
            Projection countProjection = new Projection();
            countProjection.setFunction(countQueryFunction);
            countProjection.setAliasName("Open Renewal Logo Count");


            NoQueryFunction typeNoQueryFunction = new NoQueryFunction();
            typeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                    .setAlias("type").setDataType("text");
            Projection type = new Projection();
            type.setFunction(typeNoQueryFunction);
            type.setAliasName("type");


            config.setProjectionsList(List.of(countProjection, closedatemonth, charMonth));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName(closedatemonth.getAliasName())), new AggregateConfig().setAggregateField(new QField().setName(charMonth.getAliasName()))));
            config.setOrder(List.of(new Sort(new QField().setName(closedatemonth.getAliasName()), true)));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", true)
                        );
                        predicates.add(map);
                    }

                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "{{thisyear}}")
                        );
                        predicates.add(cd);
                    }

                    if (att.getApiName().equalsIgnoreCase("type")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value","Renewal")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable thisyear = new Variable().setApiName("thisyear").setDisplayName("thisyear").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("thisyear",thisyear));

            Map<String, Object> deletedPredicate = new HashMap<>();
            deletedPredicate.put("operator", "or");
            List<Map<String, Object>> deletedPredicates = new ArrayList<>();
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });
            entityDefOpt.get().getField("isdeleted").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                deletedPredicates.add(cd);
            });

            deletedPredicate.put("predicates", deletedPredicates);
            deletedPredicate.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(deletedPredicate);


            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for openRenewalsDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("openRenewalsDS  dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }
    @ChangeSet(order = "016", id = "openRenewalsDC", author = "rohit", runAlways = true)
    public void openRenewalsDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "openRenewalsDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("openRenewalsDC").setDatasetId(datasetId);
            Variable thisyear = new Variable().setApiName("thisyear").setDisplayName("thisyear").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("thisyear",thisyear));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("openRenewalsDC").setConfig(vizConfig).setType(VizType.BAR).setDisplayName("Open Renewals");
            Datacard datacard = new Datacard().setName("openRenewalsDC")
                    .setDisplayName("Open Renewals")
                    .setDescription("Bar chart for Open Renewals")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard openRenewalsDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "017", id = "quarterlyClosedPipelineRevenueByTypeDS", author = "rohit")
    public void quarterlyClosedPipelineRevenueByTypeDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("quarterlyClosedPipelineRevenueByTypeDS").setDisplayName("Sales By Type").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            assert (entityDefOpt.isPresent());
            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            config.setFromDatasets(List.of(new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY)));

            QueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.COLUMN)))
                    .setAlias("Total").setDataType("integer");

            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Total");

            NoQueryFunction opptypeNoQueryFunction = new NoQueryFunction();
            opptypeNoQueryFunction.setColumns(List.of(new QField("type", QField.Type.COLUMN)))
                    .setAlias("opptype").setDataType("string");

            Projection opptyType = new Projection();
            opptyType.setFunction(opptypeNoQueryFunction);
            opptyType.setAliasName("opptype");

            QueryFunction concatQueryFunction = new ConcatQueryFunction();
            concatQueryFunction.setAlias("Quarter").setDataType("text")
                    .setColumns(List.of(
                            new QField("Q", QField.Type.LITERAL),
                            new QField("fiscalquarter", QField.Type.COLUMN),
                            new QField(" ", QField.Type.LITERAL),
                            new QField("fiscalyear", QField.Type.COLUMN)
                    ));

            Projection concatProjection = new Projection();
            concatProjection.setFunction(concatQueryFunction);
            concatProjection.setAliasName("Quarter");

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date")))
                    .setAlias("closedate").setDataType("date");

            Projection closeDate = new Projection();
            closeDate.setFunction(closedateNoQueryFunction);
            closeDate.setAliasName("closedate");

            config.setProjectionsList(List.of(sumProjection, concatProjection,opptyType));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear")),
                    new AggregateConfig().setAggregateField(new QField().setName("type"))));
            config.setOrder((List.of(new Sort(new QField().setName("fiscalyear"), true), new Sort(new QField().setName("fiscalquarter"), true))));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", false)
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("StageName")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "Closed Won")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("CloseDate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gt",
                                "right", Map.of("type", "literal", "value", "{{last12months}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equals("CloseDate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "lte",
                                "right", Map.of("type", "literal", "value", "{{last0days}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable last12months = new Variable().setApiName("last12months").setDisplayName("last12months").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 12 months").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("last12months",last12months,"last0days",last0days));

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for quarterlyClosedPipelineRevenueByTypeDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("quarterlyClosedPipelineRevenueByTypeDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "018", id = "quarterlyClosedPipelineRevenueByTypeDC", author = "rohit", runAlways = true)
    public void quarterlyClosedPipelineRevenueByTypeDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "quarterlyClosedPipelineRevenueByTypeDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("quarterlyClosedPipelineRevenueByTypeDC").setDatasetId(datasetId);
            Variable last12months = new Variable().setApiName("last12months").setDisplayName("last12months").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 12 months").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("last12months",last12months,"last0days",last0days));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("quarterlyClosedPipelineRevenueByTypeDC").setConfig(vizConfig).setType(VizType.LINE).setDisplayName("Sales By Type");
            Datacard datacard = new Datacard().setName("quarterlyClosedPipelineRevenueByTypeDC")
                    .setDisplayName("Sales By Type")
                    .setDescription("Quarterly Closed Pipeline Amount By Type")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard quarterlyClosedPipelineRevenueByTypeDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "019", id = "avgRevenueForAllAccountsDS", author = "rohit", runAlways = true)
    public void avgRevenueForAllAccountsDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("avgRevenueForAllAccountsDS").setDisplayName("Average sales per account").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            assert (entityDefOpt.isPresent());
            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            config.setFromDatasets(List.of(new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY)));

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

            Projection divProjection = new Projection();
            divProjection.setFunction(divQueryFunction);
            divProjection.setAliasName("Average sales per Account");
            config.setProjectionsList(List.of(divProjection));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", false)
                        );
                        predicates.add(cd);
                    }
                });
            });

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for avgRevenueForAllAccountsDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("avgRevenueForAllAccountsDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }
    @ChangeSet(order = "020", id = "avgRevenueForAllAccountsDC", author = "rohit", runAlways = true)
    public void avgRevenueForAllAccountsDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "avgRevenueForAllAccountsDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("avgRevenueForAllAccountsDC").setDatasetId(datasetId);

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("avgRevenueForAllAccountsDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Average sales per account");
            Datacard datacard = new Datacard().setName("avgRevenueForAllAccountsDC")
                    .setDisplayName("Average sales per account")
                    .setDescription("Divides the total amount of closed won opportunity amounts by the number of unique accounts")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard avgRevenueForAllAccountsDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "021", id = "existingCustomerCountDS", author = "rohit", runAlways = true)
    public void existingCustomerCountDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("existingCustomerCountDS").setDisplayName("Customer Count").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            assert (entityDefOpt.isPresent());
            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            config.setFromDatasets(List.of(new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY)));

            DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
            CountQueryFunction countQueryFunction = new CountQueryFunction();

            distinctQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                    .setDataType("text");
            countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
            countQueryFunction.setColumns(List.of(new QField("accountid", QField.Type.COLUMN)))
                    .setDataType("integer");

            Projection countProjection = new Projection();
            countProjection.setFunction(countQueryFunction);
            countProjection.setAliasName("Customer Count");
            config.setProjectionsList(List.of(countProjection));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", false)
                        );
                        predicates.add(cd);
                    }
                });
            });

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for existingCustomerCountDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("existingCustomerCountDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }
    @ChangeSet(order = "022", id = "existingCustomerCountDC", author = "rohit", runAlways = true)
    public void existingCustomerCountDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "existingCustomerCountDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("existingCustomerCountDC").setDatasetId(datasetId);

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("existingCustomerCountDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Customer Count");
            Datacard datacard = new Datacard().setName("existingCustomerCountDC")
                    .setDisplayName("Customer Count")
                    .setDescription("Number of closed won customers")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard existingCustomerCountDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "023", id = "top10CustomersByRevenueDS", author = "rohit", runAlways = true)
    public void top10CustomersByRevenueDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("top10CustomersByRevenueDS").setDisplayName("Top 10 Accounts by Sales").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            //account
            Optional<EntityDefinition>  syncariAccountEntity = schemaService.findEntity(syncariConnectorId, "account");

            assert (entityDefOpt.isPresent());
            assert (syncariAccountEntity.isPresent());

            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom opptyDataset = new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);
            DatasetFrom actDataset = new DatasetFrom().setDatasetId(syncariAccountEntity.get().getId()).setDisplayName(syncariAccountEntity.get().getDisplayName()).
                    setDatastoreName(syncariAccountEntity.get().getDataStoreName()).setApiName(syncariAccountEntity.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(opptyDataset,actDataset));

            QField opptyjoinField = new QField().setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY).setName("AccountId").setDataType("reference");
            QField accountjoinField = new QField().setDatasetId(syncariAccountEntity.get().getId()).setType(QField.Type.ENTITY).setName("syncariid").setDataType("string");

            config.setJoin(List.of(new Join().setJoinType(JoinType.Inner).setDatasetFieldFrom(opptyjoinField).setDatasetFieldTo(accountjoinField)));

            NoQueryFunction noQueryFunction = new NoQueryFunction();
            noQueryFunction.setColumns(List.of(new QField("name", QField.Type.ENTITY).setDatasetId(syncariAccountEntity.get().getId())))
                    .setAlias("Account Name").setDataType("text");

            Projection nameProjection = new Projection();
            nameProjection.setFunction(noQueryFunction);
            nameProjection.setAliasName("Account Name");

            NoQueryFunction accIdNoQueryFunction = new NoQueryFunction();
            accIdNoQueryFunction.setColumns(List.of(new QField().setName("accountid").setDataType("string").setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY)))
                    .setAlias("accountid").setDataType("text");

            Projection idProjection = new Projection();
            idProjection.setFunction(accIdNoQueryFunction);
            idProjection.setAliasName("accountid");

            SumQueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("Amount").setDataType("integer");

            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Amount");

            config.setProjectionsList(List.of(idProjection,nameProjection,sumProjection));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName("accountid").setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY)),
                    new AggregateConfig().setAggregateField(new QField().setName(nameProjection.getAliasName()).setDatasetId(syncariAccountEntity.get().getId()).setType(QField.Type.ENTITY))));
            config.setOrder((List.of(new Sort(new QField().setName("Amount"), false))));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();
            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", false)
                        );
                        predicates.add(cd);
                    }
                });
            });

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            config.setLimit(10);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for top10CustomersByRevenueDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("top10CustomersByRevenueDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "024", id = "top10CustomersByRevenueDC", author = "rohit", runAlways = true)
    public void top10CustomersByRevenueDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "top10CustomersByRevenueDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("top10CustomersByRevenueDC").setDatasetId(datasetId);

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("top10CustomersByRevenueDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Top 10 Accounts by Sales");
            Datacard datacard = new Datacard().setName("top10CustomersByRevenueDC")
                    .setDisplayName("Top 10 Accounts by Sales")
                    .setDescription("Top 10 accounts by account name and closed won opportunity")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard top10CustomersByRevenueDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "025", id = "revenueChurnByQuarterDS", author = "rohit", runAlways = true)
    public void revenueChurnByQuarterDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("revenueChurnByQuarterDS").setDisplayName("Revenue Churn By Quarter").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "opportunity");
            assert (entityDefOpt.isPresent());
            String opptyId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            config.setFromDatasets(List.of(new DatasetFrom().setDatasetId(opptyId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY)));

            SumQueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setColumns(List.of(new QField("amount", QField.Type.ENTITY).setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("Revenue").setDataType("integer");

            Projection sumProjection = new Projection();
            sumProjection.setFunction(sumQueryFunction);
            sumProjection.setAliasName("Revenue");

            ConcatQueryFunction concatQueryFunction = new ConcatQueryFunction();
            concatQueryFunction.setAlias("Quarter").setDataType("text")
                    .setColumns(List.of(
                            new QField("Q", QField.Type.LITERAL),
                            new QField("fiscalquarter", QField.Type.COLUMN),
                            new QField(" ", QField.Type.LITERAL),
                            new QField("fiscalyear", QField.Type.COLUMN)
                    ));

            Projection concatProjection = new Projection();
            concatProjection.setFunction(concatQueryFunction);
            concatProjection.setAliasName("Quarter");

            NoQueryFunction closedateNoQueryFunction = new NoQueryFunction();
            closedateNoQueryFunction.setColumns(List.of(new QField().setName("closedate").setDataType("date").setDatasetId(entityDefOpt.get().getId())))
                    .setAlias("closedate").setDataType("date");
            Projection closedateProjection = new Projection();
            closedateProjection.setFunction(closedateNoQueryFunction);
            closedateProjection.setAliasName("closedate");

            config.setProjectionsList(List.of(sumProjection,concatProjection));
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName("fiscalquarter")), new AggregateConfig().setAggregateField(new QField().setName("fiscalyear"))));
            config.setOrder((List.of(new Sort(new QField().setName("fiscalyear"), true),new Sort(new QField().setName("fiscalquarter"), true))));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();

            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equals("IsClosed")){
                        Map<String, Object> map = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("dataType", "boolean","type", "literal", "value", false)
                        );
                        predicates.add(map);
                    }
                    if (att.getApiName().equals("IsWon")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", true)
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("type")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "eq",
                                "right", Map.of("type", "literal", "value", "Renewal")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "gt",
                                "right", Map.of("type", "literal", "value","{{last12months}}")
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("closedate")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "date", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "lte",
                                "right", Map.of("type", "literal", "value","{{last0days}}")
                        );
                        predicates.add(cd);
                    }
                });
            });
            Variable last12months = new Variable().setApiName("last12months").setDisplayName("last12months").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 12 months").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("last12months",last12months,"last0days",last0days));
            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for revenueChurnByQuarterDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("revenueChurnByQuarterDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }

    @ChangeSet(order = "026", id = "revenueChurnByQuarterDC", author = "rohit", runAlways = true)
    public void revenueChurnByQuarterDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "revenueChurnByQuarterDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("revenueChurnByQuarterDC").setDatasetId(datasetId);
            Variable last12months = new Variable().setApiName("last12months").setDisplayName("last12months").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 12 months").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("last12months",last12months,"last0days",last0days));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("revenueChurnByQuarterDC").setConfig(vizConfig).setType(VizType.LINE).setDisplayName("Top 10 Accounts by Sales");
            Datacard datacard = new Datacard().setName("revenueChurnByQuarterDC")
                    .setDisplayName("Revenue Churn By Quarter")
                    .setDescription("Churn amount in closed lost opportunities from previously won accounts")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard revenueChurnByQuarterDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "027", id = "openTicketsAccountforOpenPipelineDS", author = "rohit", runAlways = true)
    public void openTicketsAccountforOpenPipelineDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("openTicketsAccountforOpenPipelineDS").setDisplayName("Open Tickets with Open Pipeline").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            //account
            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "ticket");
            Optional<EntityDefinition>  syncariAccountEntity = schemaService.findEntity(syncariConnectorId, "account");
            Optional<EntityDefinition>  syncariOppEntity = schemaService.findEntity(syncariConnectorId, "opportunity");

            assert (entityDefOpt.isPresent());
            assert (syncariAccountEntity.isPresent());

            String ticketId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom ticketDataset = new DatasetFrom().setDatasetId(ticketId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);
            DatasetFrom actDataset = new DatasetFrom().setDatasetId(syncariAccountEntity.get().getId()).setDisplayName(syncariAccountEntity.get().getDisplayName()).
                    setDatastoreName(syncariAccountEntity.get().getDataStoreName()).setApiName(syncariAccountEntity.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            DatasetFrom optDataset = new DatasetFrom().setDatasetId(syncariOppEntity.get().getId()).setDisplayName(syncariOppEntity.get().getDisplayName()).
                    setDatastoreName(syncariOppEntity.get().getDataStoreName()).setApiName(syncariOppEntity.get().getApiName()).setDatasetType(DatasourceType.ENTITY);

            config.setFromDatasets(List.of(ticketDataset,actDataset,optDataset));

            QField ticketjoinField = new QField().setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY).setName("AccountId").setDataType("reference");
            QField accountjoinField = new QField().setDatasetId(syncariAccountEntity.get().getId()).setType(QField.Type.ENTITY).setName("syncariid").setDataType("id");
            QField opptyjoinField = new QField().setDatasetId(syncariOppEntity.get().getId()).setType(QField.Type.ENTITY).setName("AccountId").setDataType("reference");

            Join join1 = new Join().setJoinType(JoinType.Inner).setDatasetFieldFrom(accountjoinField).setDatasetFieldTo(ticketjoinField);
            Join join2 = new Join().setJoinType(JoinType.Inner).setDatasetFieldFrom(accountjoinField).setDatasetFieldTo(opptyjoinField);

            config.setJoin(List.of(join1, join2));

            NoQueryFunction noQueryFunction = new NoQueryFunction();
            noQueryFunction.setColumns(List.of(new QField("name", QField.Type.ENTITY).setDatasetId(syncariAccountEntity.get().getId())))
                    .setAlias("Account Name").setDataType("text");

            Projection acctNameProjection = new Projection();
            acctNameProjection.setFunction(noQueryFunction);
            acctNameProjection.setAliasName("Account Name");

            DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
            CountQueryFunction countQueryFunction = new CountQueryFunction();

            distinctQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)))
                    .setDataType("text");
            countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
            countQueryFunction.setDataType("integer")
                    .setColumns(List.of(new QField("casenumber", QField.Type.COLUMN)));

            Projection countProjection = new Projection();
            countProjection.setFunction(countQueryFunction);
            countProjection.setAliasName("Ticket Count");

            SumQueryFunction sumQueryFunction = new SumQueryFunction();
            sumQueryFunction.setDataType("integer")
                    .setColumns(List.of(new QField("amount", QField.Type.COLUMN)));

            QueryField caseCountForDivision = new SimpleQField().setQueryFunction(countQueryFunction)
                    .setDescription("Total count of open tickets").setDisplayFormat("number");

            QueryField total = new SimpleQField().setQueryFunction(sumQueryFunction)
                    .setDescription("Open Pipeline Total Amount").setDisplayFormat("currency");

            DivideQueryFunction divQueryFunction = new DivideQueryFunction();
            divQueryFunction.setInnerQueryFields(List.of(total, caseCountForDivision))
                    .setAlias("Open Pipeline Amount").setDataType("number");

            Projection amountProject = new Projection();
            amountProject.setFunction(divQueryFunction);
            amountProject.setAliasName("Open Pipeline Amount");

            config.setProjectionsList(List.of(acctNameProjection, countProjection, amountProject));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();

            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equalsIgnoreCase("casenumber")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "not_empty",
                                "right", Map.of("type", "literal", "value", "")
                        );
                        predicates.add(cd);
                    }
                });
            });

            Map<String, Object> openticket = new HashMap<>();
            openticket.put("operator", "or");
            List<Map<String, Object>> openTicketPredicates = new ArrayList<>();
            entityDefOpt.get().getField("IsClosed").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                openTicketPredicates.add(cd);
            });
            entityDefOpt.get().getField("status").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "string"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",entityDefOpt.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", entityDefOpt.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "open"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                openTicketPredicates.add(cd);
            });

            openticket.put("predicates", openTicketPredicates);
            openticket.put("groupPredicateId", ObjectId.get().toHexString());
            predicates.add(openticket);

            syncariOppEntity.get().getField("IsClosed").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariOppEntity.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariOppEntity.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", true),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            syncariOppEntity.get().getField("Amount").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "integer"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariOppEntity.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariOppEntity.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "not_empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            syncariAccountEntity.get().getField("Name").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "string"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariAccountEntity.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariAccountEntity.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "not_empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName(acctNameProjection.getAliasName()))));
            config.setOrder(List.of(new Sort(new QField().setName(amountProject.getAliasName()), false)));
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for openTicketsAccountforOpenPipelineDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("openTicketsAccountforOpenPipelineDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }


    @ChangeSet(order = "028", id = "openTicketsAccountforOpenPipelineDC", author = "rohit", runAlways = true)
    public void openTicketsAccountforOpenPipelineDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "openTicketsAccountforOpenPipelineDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("openTicketsAccountforOpenPipelineDC").setDatasetId(datasetId);

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("openTicketsAccountforOpenPipelineDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Open Tickets with Open Pipeline");
            Datacard datacard = new Datacard().setName("openTicketsAccountforOpenPipelineDC")
                    .setDisplayName("Open Tickets with Open Pipeline")
                    .setDescription("Number of Open Tickets with Open Pipeline Accounts")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard openTicketsAccountforOpenPipelineDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }
    @ChangeSet(order = "029", id = "openTicketsCountByAccountDS", author = "rohit", runAlways = true)
    public void openTicketsCountByAccountDS(MongoTemplate template){
        try{

            Dataset dataset = new Dataset().setName("openTicketsCountByAccountDS").setDisplayName("Accounts by Open Ticket Count").setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            DatasetConfig config = new DatasetConfig();


            var connector = template.getCollection("connector");
            Document syncariConn  = connector.find(and(eq("name" ,"syncari"), eq("type", "syncari"))).first();
            String syncariConnectorId = syncariConn.getObjectId("_id").toHexString();

            //account
            Optional<EntityDefinition> entityDefOpt = schemaService.findEntity(syncariConnectorId, "ticket");
            Optional<EntityDefinition>  syncariAccountEntity = schemaService.findEntity(syncariConnectorId, "account");

            assert (entityDefOpt.isPresent());
            assert (syncariAccountEntity.isPresent());

            String ticketId = entityDefOpt.get().getId();
            String displayName = entityDefOpt.get().getDisplayName();
            DatasetFrom ticketDataset = new DatasetFrom().setDatasetId(ticketId).setDisplayName(displayName).
                    setDatastoreName(entityDefOpt.get().getDataStoreName()).setApiName(entityDefOpt.get().getApiName()).setDatasetType(DatasourceType.ENTITY);
            DatasetFrom actDataset = new DatasetFrom().setDatasetId(syncariAccountEntity.get().getId()).setDisplayName(syncariAccountEntity.get().getDisplayName()).
                    setDatastoreName(syncariAccountEntity.get().getDataStoreName()).setApiName(syncariAccountEntity.get().getApiName()).setDatasetType(DatasourceType.ENTITY);


            config.setFromDatasets(List.of(ticketDataset,actDataset));

            QField ticketjoinField = new QField().setDatasetId(entityDefOpt.get().getId()).setType(QField.Type.ENTITY).setName("AccountId").setDataType("reference");
            QField accountjoinField = new QField().setDatasetId(syncariAccountEntity.get().getId()).setType(QField.Type.ENTITY).setName("syncariid").setDataType("id");

            Join join1 = new Join().setJoinType(JoinType.Inner).setDatasetFieldFrom(accountjoinField).setDatasetFieldTo(ticketjoinField);

            config.setJoin(List.of(join1));

            NoQueryFunction noQueryFunction = new NoQueryFunction();
            noQueryFunction.setColumns(List.of(new QField("name", QField.Type.ENTITY).setDatasetId(syncariAccountEntity.get().getId())))
                    .setAlias("Account Name").setDataType("text");

            Projection acctNameProjection = new Projection();
            acctNameProjection.setFunction(noQueryFunction);
            acctNameProjection.setAliasName("Account Name");

            DistinctQueryFunction distinctQueryFunction = new DistinctQueryFunction();
            CountQueryFunction countQueryFunction = new CountQueryFunction();

            distinctQueryFunction.setColumns(List.of(new QField("casenumber", QField.Type.ENTITY)
                    .setDatasetId(entityDefOpt.get().getId()).setDataType("string")))
                    .setDataType("text");
            countQueryFunction.setInnerQueryFunction(distinctQueryFunction);
            countQueryFunction.setDataType("integer")
                    .setColumns(List.of(new QField("casenumber", QField.Type.ENTITY)
                            .setDatasetId(entityDefOpt.get().getId()).setDataType("integer"))).setAlias("Open Tickets");

            Projection countProjection = new Projection();
            countProjection.setFunction(countQueryFunction);
            countProjection.setAliasName("Open Tickets");

            config.setProjectionsList(List.of(acctNameProjection, countProjection));

            Map<String, Object> predicate = new HashMap<>();
            predicate.put("operator", "and");
            List<Map<String, Object>> predicates = new ArrayList<>();

            entityDefOpt.ifPresent(edef -> {
                List<AttributeDefinition> attributeDefinitions =  edef.getAttributes();
                attributeDefinitions.forEach(att -> {
                    if (att.getApiName().equalsIgnoreCase("isClosed")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "boolean", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "ne",
                                "right", Map.of("type", "literal", "value", true)
                        );
                        predicates.add(cd);
                    }
                    if (att.getApiName().equalsIgnoreCase("casenumber")){
                        Map<String, Object> cd = Map.of(
                                "left", Map.of("dataType", "string", "type", "variable", "value", att.getId(), "datasetId",edef.getId(), "datasetType",QField.Type.ENTITY, "fieldId", att.getId(), "apiName", att.getApiName()),
                                "operator", "not_empty",
                                "right", Map.of("type", "literal", "value", "")
                        );
                        predicates.add(cd);
                    }
                });
            });
            syncariAccountEntity.get().getField("Name").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "string"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariAccountEntity.get().getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariAccountEntity.get().getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "not_empty",
                        "right", Map.of("type", "literal", "value", ""),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            predicate.put("predicates", predicates);
            predicate.put("groupPredicateId", ObjectId.get().toHexString());
            config.setPredicate(predicate);
            config.setGroup(true);
            config.setAggregate(List.of(new AggregateConfig().setAggregateField(new QField().setName(acctNameProjection.getAliasName()))));
            config.setOrder(List.of(new Sort(new QField().setName(countProjection.getAliasName()), false)));
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);
        }catch (Exception e){
            log.error("Dataset entity information is not created for openTicketsCountByAccountDS,Exception occurred is {}.  Need to fix this for syncariId {}", e.getMessage(), SyncariContext.getSyncariId());
            log.error("Stack trace is {}", ExceptionUtils.getStackTrace(e));
            log.error("openTicketsCountByAccountDS dataset won't be added for syncariId {}",SyncariContext.getSyncariId());
        }
    }


    @ChangeSet(order = "030", id = "openTicketsCountByAccountDC", author = "rohit", runAlways = true)
    public void openTicketsCountByAccountDC(MongoTemplate template){
        try {
            MongoCollection<Document> datasetCollection = template.getCollection("dataset");
            // find dataset
            Document dataset = datasetCollection.find(new Document("name", "openTicketsCountByAccountDS")).first();
            String datasetId = dataset.getObjectId("_id").toHexString();
            VizConfig vizConfig = new VizConfig().setName("openTicketsCountByAccountDC").setDatasetId(datasetId);

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("openTicketsCountByAccountDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Accounts by Open Ticket Count");
            Datacard datacard = new Datacard().setName("openTicketsCountByAccountDC")
                    .setDisplayName("Accounts by Open Ticket Count")
                    .setDescription("Accounts by Open Ticket Count")
                    .setContents(List.of(visualization))
                    .setSeeded(true);
            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.error("Could not create datacard openTicketsCountByAccountDC Stack trace is {}", ExceptionUtils.getStackTrace(e));
        }
    }

    private static LocalDateTime getCurrentQuarterFirsDate(){
        LocalDateTime localDate = LocalDateTime.now();
        return localDate.with(localDate.getMonth().firstMonthOfQuarter())
                .with(TemporalAdjusters.firstDayOfMonth());
    }
    private Dataset updateDatasetIfExists(Dataset dataset){
        Optional<Dataset> existing = datasetService.findDatasetByName(dataset.getName());
        if(existing.isPresent()){
            return datasetService.updateDataset(existing.get().getId(), dataset);
        } else {
            return datasetService.createDataset(dataset);
        }
    }

    private Datacard updateDatacardIfExists(Datacard datacard){
        Optional<Datacard> existing = datacardService.findDatacardByName(datacard.getName());
        if(existing.isPresent()){
            return datacardService.updateDatacard(existing.get().getId(), datacard);
        } else {
            return datacardService.createDatacard(datacard);
        }
    }
}
