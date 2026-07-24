package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.insights.AggregateConfig;
import com.syncari.core.model.insights.BarVizConfig;
import com.syncari.core.model.insights.CountQueryFunction;
import com.syncari.core.model.insights.Datacard;
import com.syncari.core.model.insights.DateTruncQueryFunction;
import com.syncari.core.model.insights.JoinType;
import com.syncari.core.model.insights.LineVizConfig;
import com.syncari.core.model.insights.MetricVizConfig;
import com.syncari.core.model.insights.NoQueryFunction;
import com.syncari.core.model.insights.Projection;
import com.syncari.core.model.insights.QField;
import com.syncari.core.model.insights.SimpleQField;
import com.syncari.core.model.insights.TableVizConfig;
import com.syncari.core.model.insights.ToCharQueryFunction;
import com.syncari.core.model.insights.Visualization;
import com.syncari.core.model.insights.VizConfig;
import com.syncari.core.model.insights.VizType;
import com.syncari.core.model.insights.dataset.*;
import com.syncari.core.service.DatacardService;
import com.syncari.core.service.DatasetService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Slf4j
public class UpdateInsightsDatasetAndDatacardToV2Contd {
    private final DatasetService datasetService = MigrationContext.getDatasetService();
    private final DatacardService datacardService = MigrationContext.getDatacardService();
    private final SchemaService schemaService = MigrationContext.getSchemaService();

    @ChangeSet(order = "001", id = "openEscalatedTicketCountDS", author = "abhinav", runAlways = true)
    public void openEscalatedTicketCountDS(MongoTemplate template){

        try {
            Dataset dataset = new Dataset().setName("openEscalatedTicketCountDS").setDisplayName("Open Escalated Ticket Count")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariTicketEntity = schemaService.getSyncariEntityByName("ticket")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'ticket' does not exist")));

            DatasetConfig config = new DatasetConfig();
            var ticketCountFunction = new CountQueryFunction();
            ticketCountFunction.setColumns(List.of(new QField("CaseNumber", QField.Type.ENTITY)
                    .setDatasetId(syncariTicketEntity.getId()).setDataType("string")));
            ticketCountFunction.setAlias("Open Escalated Ticket Count").setDataType("integer");
            Projection ticketCount = new Projection()
                    .setAliasName("Open Escalated Ticket Count")
                    .setFunction(ticketCountFunction);

            config.setProjectionsList(List.of(ticketCount));
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariTicketEntity)));

            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariTicketEntity.getAttributes().forEach(att -> {
                if (att.getApiName().equals("IsEscalated")){
                    Map<String, Object> map = Map.of(
                            "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                    Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                    Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                    Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                    Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                            "operator", "eq",
                            "right", Map.of("type", "literal", "value", "true"),
                            "predicateId", ObjectId.get().toHexString(),
                            "name", "filter"
                    );
                    predicates.add(map);
                }

                if (att.getApiName().equalsIgnoreCase("IsClosed")){
                    Map<String, Object> cd = Map.of(
                            "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                    Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                    Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                    Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                    Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                            "operator", "ne",
                            "right", Map.of("type", "literal", "value", "true"),
                            "predicateId", ObjectId.get().toHexString(),
                            "name", "filter"
                    );
                    predicates.add(cd);
                }
            });
            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'Open Escalated Ticket Count Dataset' creation failed.", e);
        }
    }

    @ChangeSet(order = "002", id = "openEscalatedTicketCountDC", author = "abhinav", runAlways = true)
    public void openEscalatedTicketCountDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("openEscalatedTicketCountDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'openEscalatedTicketCountDS' not found"));

            VizConfig vizConfig = new MetricVizConfig().setName("openEscalatedTicketCountDC").setDatasetId(dataset.getId());
            SimpleQField field = new SimpleQField();
            field.getQueryFunction()
                    .setColumns(List.of(new QField().setName("Open Escalated Ticket Count").setType(QField.Type.DATASET)))
                    .setAlias("Open Escalated Ticket Count");
            field.setDisplayFormat("number");
            vizConfig.setColumns(List.of(field));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("openEscalatedTicketCountDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Open Escalated Ticket Count");
            Datacard datacard = new Datacard().setName("openEscalatedTicketCountDC")
                    .setDisplayName("Open Escalated Ticket Count")
                    .setDescription("Open Escalated Ticket Count")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("openEscalatedTicketCountDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "005", id = "openTicketsByPriorityDS", author = "abhinav", runAlways = true)
    public void openTicketsByPriorityDS(MongoTemplate template){
        try {
            Dataset dataset = new Dataset().setName("openTicketsByPriorityDS").setDisplayName("Issues By Priority")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariTicketEntity = schemaService.getSyncariEntityByName("ticket")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'ticket' does not exist")));

            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariTicketEntity)));

            // add projection - Priority and ticket count
            var priorityFunc = new NoQueryFunction();
            priorityFunc.setColumns(List.of(new QField("Priority", QField.Type.ENTITY)
                    .setDatasetId(syncariTicketEntity.getId()).setDataType("picklist")));
            priorityFunc.setAlias("Priority").setDataType("string");
            var priority = new Projection()
                    .setAliasName("Priority")
                    .setFunction(priorityFunc);

            var ticketCountFunction = new CountQueryFunction();
            ticketCountFunction.setColumns(List.of(new QField("priority", QField.Type.ENTITY)
                    .setDatasetId(syncariTicketEntity.getId()).setDataType("string")));
            ticketCountFunction.setAlias("Tickets Count").setDataType("integer");
            Projection ticketCount = new Projection()
                    .setAliasName("Tickets Count")
                    .setFunction(ticketCountFunction);
            config.setProjectionsList(List.of(priority, ticketCount));
            config.setOrder(List.of(new Sort(new QField().setName("Tickets Count"), false)));

            // add aggregate on Account name
            AggregateConfig aggConfig = new AggregateConfig()
                    .setAggregateField(new QField("Priority", QField.Type.ENTITY)
                            .setDatasetId(syncariTicketEntity.getId()).setDataType("picklist"));
            config.setAggregate(List.of(aggConfig));
            config.setGroup(true);

            // add predicates
            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariTicketEntity.getField("IsClosed").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "boolean"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                        "operator", "ne",
                        "right", Map.of("type", "literal", "value", "true"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);
            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'Issues By Priority' creation failed.", e);
        }
    }

    @ChangeSet(order = "006", id = "openTicketsByPriorityDC", author = "abhinav", runAlways = true)
    public void openTicketsByPriorityDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("openTicketsByPriorityDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'openTicketsByPriorityDS' not found"));

            BarVizConfig vizConfig = new BarVizConfig();
            vizConfig.setName("openTicketsByPriorityDC").setDatasetId(dataset.getId());
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
            Visualization visualization = new Visualization().setName("openTicketsByPriorityDC").setConfig(vizConfig).setType(VizType.COLUMN).setDisplayName("Accounts By Open Ticket Count");
            Datacard datacard = new Datacard().setName("openTicketsByPriorityDC")
                    .setDisplayName("Issues By Priority")
                    .setDescription("The count of open issues by priority")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("openTicketsByPriorityDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "007", id = "leadsBySourceDS", author = "abhinav", runAlways = true)
    public void leadsBySourceDS(MongoTemplate template){

        try {
            Dataset dataset = new Dataset().setName("leadsBySourceDS").setDisplayName("Marketing Attribution Funnel")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariLeadEntity = schemaService.getSyncariEntityByName("lead")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'lead' does not exist")));

            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariLeadEntity)));

            // add projection - Priority and ticket count
            var leadSourceFunc = new NoQueryFunction();
            leadSourceFunc.setColumns(List.of(new QField("LeadSource", QField.Type.ENTITY)
                    .setDatasetId(syncariLeadEntity.getId()).setDataType("picklist")));
            leadSourceFunc.setAlias("Lead Source").setDataType("string");
            var leadSource = new Projection()
                    .setAliasName("Lead Source")
                    .setFunction(leadSourceFunc);

            var leadCountFunction = new CountQueryFunction();
            leadCountFunction.setColumns(List.of(new QField("syncariid", QField.Type.ENTITY)
                    .setDatasetId(syncariLeadEntity.getId()).setDataType("string")));
            leadCountFunction.setAlias("Lead Count").setDataType("integer");
            Projection leadCount = new Projection()
                    .setAliasName("Lead Count")
                    .setFunction(leadCountFunction);
            config.setProjectionsList(List.of(leadSource, leadCount));

            // add aggregate on Account name
            AggregateConfig aggConfig = new AggregateConfig()
                    .setAggregateField(new QField("LeadSource", QField.Type.ENTITY)
                            .setDatasetId(syncariLeadEntity.getId()).setDataType("picklist"));
            config.setAggregate(List.of(aggConfig));
            config.setOrder(List.of(new Sort(new QField().setName(leadCount.getAliasName()), false)));
            config.setGroup(true);

            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'Lead By Source' creation failed.", e);
        }
    }

    @ChangeSet(order = "008", id = "leadsBySourceDC", author = "abhinav", runAlways = true)
    public void leadsBySourceDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("leadsBySourceDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'leadsBySourceDS' not found"));

            TableVizConfig vizConfig = new TableVizConfig();
            vizConfig.setName("leadsBySourceDC").setDatasetId(dataset.getId());
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
            Visualization visualization = new Visualization().setName("leadsBySourceDC").setConfig(vizConfig).setType(VizType.TABLE).setDisplayName("Accounts By Open Ticket Count");
            Datacard datacard = new Datacard().setName("leadsBySourceDC")
                    .setDisplayName("Marketing Attribution Funnel")
                    .setDescription("Distribution of leads by source")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("leadsBySourceDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "009", id = "mqlCountInQuarterDS", author = "abhinav", runAlways = true)
    public void mqlCountInQuarterDS(MongoTemplate template){

        try {
            Dataset dataset = new Dataset().setName("mqlCountInQuarterDS").setDisplayName("Qualified Lead Count")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariLeadEntity = schemaService.getSyncariEntityByName("lead")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'lead' does not exist")));

            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariLeadEntity)));

            // add projection - Priority and ticket count
            var leadCountFunction = new CountQueryFunction();
            leadCountFunction.setColumns(List.of(new QField("syncariid", QField.Type.ENTITY)
                    .setDatasetId(syncariLeadEntity.getId()).setDataType("string")));
            leadCountFunction.setAlias("Lead Count").setDataType("integer");
            Projection leadCount = new Projection()
                    .setAliasName("Lead Count")
                    .setFunction(leadCountFunction);
            config.setProjectionsList(List.of(leadCount));

            // add predicates
            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariLeadEntity.getField("Status").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "picklist"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariLeadEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariLeadEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "Qualified"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            syncariLeadEntity.getField("CreatedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariLeadEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariLeadEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "{{thisquarter}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            Variable thisquarter = new Variable().setApiName("thisquarter").setDisplayName("thisquarter").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this quarter").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("thisquarter",thisquarter));
            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);

            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("mqlCountInQuarterDS creation failed.", e);
        }
    }

    @ChangeSet(order = "010", id = "mqlCountInQuarterDC", author = "abhinav", runAlways = true)
    public void mqlCountInQuarterDC(MongoTemplate template){
        try {
            Dataset dataset = datasetService.findDatasetByName("mqlCountInQuarterDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'mqlCountInQuarterDS' not found"));

            MetricVizConfig vizConfig = new MetricVizConfig();
            vizConfig.setName("mqlCountInQuarterDC").setDatasetId(dataset.getId());

            SimpleQField leadCountField = new SimpleQField();
            leadCountField.getQueryFunction()
                    .setColumns(List.of(new QField().setName("Lead Count").setType(QField.Type.DATASET)))
                    .setAlias("Lead Count");
            leadCountField.setDisplayFormat("number");
            vizConfig.setColumns(List.of(leadCountField));
            Variable thisquarter = new Variable().setApiName("thisquarter").setDisplayName("thisquarter").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this quarter").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("thisquarter",thisquarter));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("mqlCountInQuarterDC").setConfig(vizConfig).setType(VizType.METRIC).setDisplayName("Qualified Lead Count");
            Datacard datacard = new Datacard().setName("mqlCountInQuarterDC")
                    .setDisplayName("Qualified Lead Count")
                    .setDescription("Qualified Lead Count")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
        log.info("mqlCountInQuarterDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "011", id = "sqlCountByOwnerDS", author = "abhinav", runAlways = true)
    public void sqlCountByOwnerDS(MongoTemplate template){
        try {
            Dataset dataset = new Dataset().setName("sqlCountByOwnerDS").setDisplayName("Qualified Leads by Owner")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariLeadEntity = schemaService.getSyncariEntityByName("lead")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'lead' does not exist")));

            EntityDefinition syncariUserEntity = schemaService.getSyncariEntityByName("user")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'user' does not exist")));

            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(
                    new DatasetFrom().populateFromEntity(syncariLeadEntity),
                    new DatasetFrom().populateFromEntity(syncariUserEntity)));

            // add lead and user join
            Join join = new Join()
                    .setDatasetFieldFrom(new QField("OwnerId", QField.Type.ENTITY)
                            .setDatasetId(syncariLeadEntity.getId()).setDataType("reference"))
                    .setDatasetFieldTo(new QField("Id", QField.Type.ENTITY)
                            .setDatasetId(syncariUserEntity.getId()).setDataType("id"))
                    .setJoinType(JoinType.Inner);
            config.setJoin(List.of(join));

            // add projection - owner name and lead count
            var ownerNameFunc = new NoQueryFunction();
            ownerNameFunc.setColumns(List.of(new QField("Name", QField.Type.ENTITY)
                    .setDatasetId(syncariUserEntity.getId()).setDataType("string")));
            ownerNameFunc.setAlias("Owner Name").setDataType("string");
            var ownerName = new Projection()
                    .setAliasName("Owner Name")
                    .setFunction(ownerNameFunc);

            var leadCountFunction = new CountQueryFunction();
            leadCountFunction.setColumns(List.of(new QField("syncariid", QField.Type.ENTITY)
                    .setDatasetId(syncariLeadEntity.getId()).setDataType("string")));
            leadCountFunction.setAlias("Qualified Lead Count").setDataType("integer");
            Projection leadCount = new Projection()
                    .setAliasName("Qualified Lead Count")
                    .setFunction(leadCountFunction);
            config.setProjectionsList(List.of(leadCount, ownerName));

            // add aggregate on Account name
            AggregateConfig aggConfig = new AggregateConfig()
                    .setAggregateField(new QField("Name", QField.Type.ENTITY)
                            .setDatasetId(syncariUserEntity.getId()).setDataType("string"));
            config.setAggregate(List.of(aggConfig));
            config.setGroup(true);

            // add predicates
            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariLeadEntity.getField("Status").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "picklist"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariLeadEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariLeadEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "Qualified"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            syncariLeadEntity.getField("CreatedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariLeadEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariLeadEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "{{thisquarter}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            Variable thisquarter = new Variable().setApiName("thisquarter").setDisplayName("thisquarter").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this quarter").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            dataset.setVariablesMap(Map.of("thisquarter",thisquarter));
            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);

            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'Qualified Leads by Owner' creation failed.", e);
        }
    }

    @ChangeSet(order = "012", id = "sqlCountByOwnerDC", author = "abhinav", runAlways = true)
    public void sqlCountByOwnerDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("sqlCountByOwnerDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'sqlCountByOwnerDS' not found"));

            TableVizConfig vizConfig = new TableVizConfig();
            vizConfig.setName("sqlCountByOwnerDC").setDatasetId(dataset.getId());
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

            Variable thisquarter = new Variable().setApiName("thisquarter").setDisplayName("thisquarter").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this quarter").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            vizConfig.setVariablesMap(Map.of("thisquarter",thisquarter));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("sqlCountByOwnerDC").setConfig(vizConfig)
                    .setType(VizType.TABLE).setDisplayName("Qualified Leads by Owner");
            Datacard datacard = new Datacard().setName("sqlCountByOwnerDC")
                    .setDisplayName("Qualified Leads by Owner")
                    .setDescription("Leads in qualified status listed by owner")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("sqlCountByOwnerDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }
    }

    @ChangeSet(order = "013", id = "userGrowthDS", author = "abhinav", runAlways = true)
    public void userGrowthDS(MongoTemplate template){
        try {
            Dataset dataset = new Dataset().setName("userGrowthDS").setDisplayName("User Growth")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariUserEntity = schemaService.getSyncariEntityByName("user")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'user' does not exist")));
            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariUserEntity)));

            // add projection - owner name and lead count
            var createdMonthFunc = new ToCharQueryFunction();
            createdMonthFunc.setColumns(List.of(
                    new QField("CreatedDate", QField.Type.ENTITY).setDatasetId(syncariUserEntity.getId()).setDataType("string")));
            createdMonthFunc.setAlias("Month").setDataType("string");
            createdMonthFunc.setToCharField("Mon-YYYY");
            var createdMonth = new Projection()
                    .setAliasName("Month")
                    .setFunction(createdMonthFunc);

            var userCountFunction = new CountQueryFunction();
            userCountFunction.setColumns(List.of(new QField("syncariid", QField.Type.ENTITY)
                    .setDatasetId(syncariUserEntity.getId()).setDataType("string")));
            userCountFunction.setAlias("User Count").setDataType("integer");
            Projection userCount = new Projection()
                    .setAliasName("User Count")
                    .setFunction(userCountFunction);

            var createdDateMonthFunc = new DateTruncQueryFunction();
            createdDateMonthFunc.setColumns(List.of(
                    new QField("CreatedDate", QField.Type.ENTITY).setDatasetId(syncariUserEntity.getId()).setDataType("string")));
            createdDateMonthFunc.setTruncatedField("month");
            createdDateMonthFunc.setAlias("Created Date As Month").setDataType("string");
            var createdDateMonth = new Projection()
                    .setAliasName("Created Date As Month")
                    .setFunction(createdDateMonthFunc);
            config.setProjectionsList(List.of(createdMonth, userCount, createdDateMonth));

            // add aggregate on Account name
            AggregateConfig aggConfig1 = new AggregateConfig()
                    .setAggregateField(new QField("Month", QField.Type.DATASET));
            AggregateConfig aggConfig2 = new AggregateConfig()
                    .setAggregateField(new QField("Created Date As Month", QField.Type.DATASET));
            config.setAggregate(List.of(aggConfig1, aggConfig2));
            config.setOrder(List.of(new Sort(new QField().setName(createdDateMonthFunc.getAlias()), true)));
            config.setGroup(true);

            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariUserEntity.getField("CreatedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariUserEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariUserEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "{{thisyear}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            Variable thisyear = new Variable().setApiName("thisyear").setDisplayName("thisyear").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            dataset.setVariablesMap(Map.of("thisyear",thisyear));

            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);

            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'User Growth Dataset' creation failed.", e);
        }
    }

    @ChangeSet(order = "014", id = "userGrowthDC", author = "abhinav", runAlways = true)
    public void userGrowthDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("userGrowthDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'userGrowthDS' not found"));

            LineVizConfig vizConfig = new LineVizConfig();
            vizConfig.setName("userGrowthDC").setDatasetId(dataset.getId());
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
            vizConfig.setColumns(List.of(userCountField));
            vizConfig.setXAxis(monthField);
            vizConfig.setYAxis(List.of(userCountField));
            Variable thisyear = new Variable().setApiName("thisyear").setDisplayName("thisyear").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("this year").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));
            vizConfig.setVariablesMap(Map.of("thisyear",thisyear));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("userGrowthDC").setConfig(vizConfig)
                    .setType(VizType.LINE).setDisplayName("User Growth");
            Datacard datacard = new Datacard().setName("userGrowthDC")
                    .setDisplayName("User Growth")
                    .setDescription("Monthly User Growth")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("userGrowthDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "015", id = "trendOfIssuesResolvedIn7DaysDS", author = "abhinav", runAlways = true)
    public void trendOfIssuesResolvedIn7DaysDS(MongoTemplate template){
        try {
            Dataset dataset = new Dataset().setName("trendOfIssuesResolvedIn7DaysDS").setDisplayName("Closed Ticket Trend 7 days")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariTicketEntity = schemaService.getSyncariEntityByName("ticket")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'ticket' does not exist")));
            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariTicketEntity)));

            // add projection - owner name and lead count
            var closedDateFunc = new ToCharQueryFunction();
            closedDateFunc.setColumns(List.of(
                    new QField("ClosedDate", QField.Type.ENTITY).setDatasetId(syncariTicketEntity.getId()).setDataType("datetime"),
                    new QField("MM-DD-YYYY", QField.Type.LITERAL).setDataType("string")));
            closedDateFunc.setAlias("Closed Date").setDataType("string");
            closedDateFunc.setToCharField("MM-DD-YYYY");
            var closedDate = new Projection()
                    .setAliasName("Closed Date")
                    .setFunction(closedDateFunc);

            var ticketCountFunction = new CountQueryFunction();
            ticketCountFunction.setColumns(List.of(new QField("CaseNumber", QField.Type.ENTITY)
                    .setDatasetId(syncariTicketEntity.getId()).setDataType("string")));
            ticketCountFunction.setAlias("Ticket Count").setDataType("integer");
            Projection ticketCount = new Projection()
                    .setAliasName("Ticket Count")
                    .setFunction(ticketCountFunction);

            config.setProjectionsList(List.of(closedDate, ticketCount));

            // add aggregate on Account name
            AggregateConfig aggConfig1 = new AggregateConfig()
                    .setAggregateField(new QField("Closed Date", QField.Type.DATASET));
            config.setAggregate(List.of(aggConfig1));
            config.setGroup(true);

            // add predicates
            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariTicketEntity.getField("Status").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "picklist"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "Closed"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);


            });

            syncariTicketEntity.getField("ClosedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "gt",
                        "right", Map.of("type", "literal", "value", "{{last7days}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            syncariTicketEntity.getField("ClosedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "lte",
                        "right", Map.of("type", "literal", "value", "{{last0days}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });
            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable last7days = new Variable().setApiName("last7days").setDisplayName("last7days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 7 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("last0days",last0days,"last7days",last7days));

            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);

            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'Closed Ticket Trend 7 days' creation failed.", e);
        }
    }

    @ChangeSet(order = "016", id = "trendOfIssuesResolvedIn7DaysDC", author = "abhinav", runAlways = true)
    public void trendOfIssuesResolvedIn7DaysDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("trendOfIssuesResolvedIn7DaysDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'trendOfIssuesResolvedIn7DaysDS' not found"));

            LineVizConfig vizConfig = new LineVizConfig();
            vizConfig.setName("trendOfIssuesResolvedIn7DaysDC").setDatasetId(dataset.getId());
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

            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable last7days = new Variable().setApiName("last7days").setDisplayName("last7days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 7 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("last0days",last0days,"last7days",last7days));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("trendOfIssuesResolvedIn7DaysDC").setConfig(vizConfig)
                    .setType(VizType.LINE).setDisplayName("Closed Ticket Trend 7 days");
            Datacard datacard = new Datacard().setName("trendOfIssuesResolvedIn7DaysDC")
                    .setDisplayName("Closed Ticket Trend 7 days")
                    .setDescription("Trend of Closed Ticket in 7 days")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("trendOfIssuesResolvedIn7DaysDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }

    }

    @ChangeSet(order = "017", id = "trendOfIssuesResolvedIn24HoursDS", author = "abhinav", runAlways = true)
    public void trendOfIssuesResolvedIn24HoursDS(MongoTemplate template){
        try {
            Dataset dataset = new Dataset().setName("trendOfIssuesResolvedIn24HoursDS").setDisplayName("Closed Ticket Trend in 24 hours")
                    .setVersion("v1").setSeeded(true);
            dataset.setDraftStatus(DraftStatus.APPROVED);
            EntityDefinition syncariTicketEntity = schemaService.getSyncariEntityByName("ticket")
                    .orElseThrow(() -> new RuntimeException(String.format("Syncari entity 'ticket' does not exist")));
            DatasetConfig config = new DatasetConfig();
            // set from datasets
            config.setFromDatasets(List.of(new DatasetFrom().populateFromEntity(syncariTicketEntity)));

            // add projection - owner name and lead count
            var closedHourFunc = new ToCharQueryFunction();
            closedHourFunc.setColumns(List.of(
                    new QField("ClosedDate", QField.Type.ENTITY).setDatasetId(syncariTicketEntity.getId()).setDataType("datetime"),
                    new QField("hh24:00", QField.Type.LITERAL).setDataType("string")));
            closedHourFunc.setAlias("Closed Hour").setDataType("string");
            closedHourFunc.setToCharField("hh24:00");
            var closedDate = new Projection()
                    .setAliasName("Closed Hour")
                    .setFunction(closedHourFunc);

            var ticketCountFunction = new CountQueryFunction();
            ticketCountFunction.setColumns(List.of(new QField("CaseNumber", QField.Type.ENTITY)
                    .setDatasetId(syncariTicketEntity.getId()).setDataType("string")));
            ticketCountFunction.setAlias("Ticket Count").setDataType("integer");
            Projection ticketCount = new Projection()
                    .setAliasName("Ticket Count")
                    .setFunction(ticketCountFunction);

            config.setProjectionsList(List.of(closedDate, ticketCount));

            // add aggregate on Account name
            AggregateConfig aggConfig1 = new AggregateConfig()
                    .setAggregateField(new QField("Closed Hour", QField.Type.DATASET));
            config.setAggregate(List.of(aggConfig1));
            config.setGroup(true);

            // add predicates
            List<Map<String, Object>> predicates = new ArrayList<>();
            syncariTicketEntity.getField("Status").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", "picklist"), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId()), Map.entry("renderType", "datasetVariablePicker")),
                        "operator", "eq",
                        "right", Map.of("type", "literal", "value", "Closed"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            syncariTicketEntity.getField("ClosedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "gt",
                        "right", Map.of("type", "literal", "value", "{{last24hours}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            syncariTicketEntity.getField("ClosedDate").ifPresent(att -> {
                Map<String, Object> cd = Map.of(
                        "left", Map.ofEntries(Map.entry("dataType", att.getDataType().getName()), Map.entry("type", "variable"), Map.entry("value", att.getId()),
                                Map.entry("datasetId",syncariTicketEntity.getId()), Map.entry("datasetType",QField.Type.ENTITY),
                                Map.entry("fieldId", att.getId()), Map.entry("apiName", att.getApiName()), Map.entry("displayName", att.getDisplayName()),
                                Map.entry("alias", String.format("%s:%s", syncariTicketEntity.getDisplayName(), att.getDisplayName())),
                                Map.entry("id", att.getId())),
                        "operator", "lte",
                        "right", Map.of("type", "literal", "value", "{{last0days}}"),
                        "predicateId", ObjectId.get().toHexString(),
                        "name", "filter"
                );
                predicates.add(cd);
            });

            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable last24hours = new Variable().setApiName("last24hours").setDisplayName("last24hours").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 24 hours").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            dataset.setVariablesMap(Map.of("last0days",last0days,"last24hours",last24hours));

            Map<String, Object> predicate = Map.of("predicates", predicates, "groupPredicateId", ObjectId.get().toHexString(), "operator", "AND");
            config.setPredicate(predicate);

            dataset.setDatasetConfig(config);
            updateDatasetIfExists(dataset);

        } catch (Exception e){
            log.error("'Closed Ticket Trend 7 days' creation failed.", e);
        }
    }

    @ChangeSet(order = "018", id = "trendOfIssuesResolvedIn24HoursDC", author = "abhinav", runAlways = true)
    public void trendOfIssuesResolvedIn24HoursDC(MongoTemplate template){
        try{
            Dataset dataset = datasetService.findDatasetByName("trendOfIssuesResolvedIn24HoursDS")
                    .orElseThrow(() -> new RuntimeException("Dataset 'trendOfIssuesResolvedIn24HoursDS' not found"));

            LineVizConfig vizConfig = new LineVizConfig();
            vizConfig.setName("trendOfIssuesResolvedIn24HoursDC").setDatasetId(dataset.getId());
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

            Variable last0days = new Variable().setApiName("last0days").setDisplayName("last0days").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 0 days").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            Variable last24hours = new Variable().setApiName("last24hours").setDisplayName("last24hours").setDatatype("datetime").setRequired(true).setUpdatable(true)
                    .setVariableValue(new VariableValue().setDefaultValue("last 24 hours").setDefaultValueType(VariableValue.VariableType.LITERAL).setDatatype("datetime"));

            vizConfig.setVariablesMap(Map.of("last0days",last0days,"last24hours",last24hours));

            // create datacard with single visualization
            Visualization visualization = new Visualization().setName("trendOfIssuesResolvedIn24HoursDC").setConfig(vizConfig)
                    .setType(VizType.LINE).setDisplayName("Closed Ticket Trend 7 days");
            Datacard datacard = new Datacard().setName("trendOfIssuesResolvedIn24HoursDC")
                    .setDisplayName("Closed Ticket Trend in 24 hours")
                    .setDescription("Trend of Closed Ticket in 24 Hours")
                    .setContents(List.of(visualization))
                    .setSeeded(true);

            updateDatacardIfExists(datacard);
        }catch (Exception e){
            log.info("trendOfIssuesResolvedIn24HoursDC not created for exception {}", ExceptionUtils.getStackTrace(e));
        }

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
