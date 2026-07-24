package com.syncari.core.functions;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.Result;
import com.syncari.connector.data.SyncRequest;
import com.syncari.connector.data.SyncResponse;
import com.syncari.core.SyncariContext;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.DefaultAction;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableFunctionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.quickstart.v2.dependency.ExpressionDependencyResolver;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TransactionLogService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(ActionConstants.INSERT_SYNCARI_RECORD)
@Slf4j
public class InsertSyncariRecordAction extends DefaultAction {
    private static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";

    @Autowired
    SchemaService schemaService;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    TransactionLogService transactionLogService;

    @Override
    public void validate(ValidationContext validationContext) {
        var errors = validateWithoutException(validationContext);
        if (errors != null && !errors.isEmpty()) {
            throw new SyncariValidationException(errors.get(0).getMessage());
        }
    }

    @Override
    public List<ValidationError> validateWithoutException(ValidationContext validationContext) {
        List<ValidationError> errors = new ArrayList<ValidationError>();
        errors.addAll(super.validateWithoutException(validationContext));
        MappingNode node = validationContext.getNode();
        MappingGraph graph = validationContext.getGraph();

        if (graph == null || node == null)
            return errors;

        GenericActionConfig actionConfig = node.getTypedConfiguration();
        Optional<ActionDefinition> actionDefinition = getActionDefinition(node, actionConfig);
        Map<String, String> configNameLabelMap = actionDefinition.stream().flatMap(a -> a.getConfiguration().stream()).collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
        Map<String, Object> configMap = actionConfig.getConfigMap();

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID);
        if (syncariEntityDefId == null) {
            return errors;
        }
        Optional<EntityDefinition> syncariEntityMaybe = schemaService.findEntity(syncariEntityDefId.toString());
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), syncariEntityMaybe.isEmpty(),
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID), syncariEntityDefId,
                        node.getName(), graph.getName()), ErrorCode.E1106.getCode()).ifPresent(e -> errors.add(e));

        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("insertFields");
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()),
                updateFields == null || updateFields.isEmpty(), i18n("invalid_config_in_node",
                        configNameLabelMap.get("insertFields"), "Empty Fields", node.getName(), graph.getName()), ErrorCode.E1107.getCode())
                .ifPresent(e -> errors.add(e));
        if (syncariEntityMaybe.isEmpty()) {
            return errors;
        }
        EntityDefinition syncariEntity = syncariEntityMaybe.get();
        validationContext.getData().put("syncariEntity", syncariEntity);

        // inputFieldId refers to attribute of connected sources or core entity
        // validate each field
        for (Map<String, Map<String, String>> s : updateFields) {
            validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), s.get("updateField") == null,
                    i18n("update_records_empty_attribute", validationContext.getNode().getName(),
                            validationContext.getGraph().getName()), ErrorCode.E1108.getCode()).ifPresent(e -> errors.add(e));
            if (s.get("updateField") != null) {
                String attributeId = s.get("updateField").get("value");
                AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
                validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), attribute == null,
                        i18n("insert_record_invalid_attribute", validationContext.getNode().getName(),
                                validationContext.getGraph().getName()), ErrorCode.E1109.getCode()).ifPresent(e -> errors.add(e));
            }
        }

        return errors;
    }

    @Override
    public void extract(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode node = context.getCurrentNode();
        super.extract(context);
        SharableActionNodeConfig actionNodeConfig = node.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();
        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition syncariEntity = context.getEntity(syncariEntityDefId).orElseThrow();
        qsConfig.addDependency(DependencyUtil.getEntityDependency(syncariEntity));

        // 2. attributes from insertFields config
        List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get("insertFields");
        for (Map<String, Map<String, String>> s : updateFields) {
            String attributeId = s.get("updateField").get("value");
            AttributeDefinition attribute = context.getAttribute(attributeId).orElseThrow();
            qsConfig.addDependency(DependencyUtil.getAttributeDependency(attribute));
        }
    }

    @Override
    public MappingNode resolve(QuickStartContext context) {
        PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
        SharableNode sharableNode = context.getCurrentNode().copy();
        SharableActionNodeConfig actionNodeConfig = sharableNode.getTypedConfiguration();
        Map<String, Object> configMap = actionNodeConfig.getConfigMap();

        // 1. Selected Syncari entity
        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID).toString();
        EntityDefinition resolvedEntity = (EntityDefinition) qsConfig.getResolvedValueByType(syncariEntityDefId, QSDependency.Type.Entity);
        if (resolvedEntity != null) {
            configMap.put(SYNCARI_ENTITY_DEF_ID, resolvedEntity.getId());
        }

        // 2. attributes from insertFields config. Logic moved to post process
        actionNodeConfig.setConfigMap(configMap);
        sharableNode.setConfiguration(actionNodeConfig);
        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            if ("insertFields".equals(configProperty)) {
                List<Pair<String, String>> res = new ArrayList<Pair<String, String>>();
                GenericActionConfig actionConfig = context.getCurrentNode().getTypedConfiguration();
                Map<String, Object> configMap = actionConfig.getConfigMap();
                var syncariEntityDefId = configMap.getOrDefault(SYNCARI_ENTITY_DEF_ID, configProperty);
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
                if (entityDefinition.isPresent()) {
                    List<Map<String, Map<String, String>>> updateFields = (List<Map<String, Map<String, String>>>) configMap.get(configProperty);
                    int i = 1;
                    for (Map<String, Map<String, String>> s : updateFields) {
                        List<String> col = new ArrayList<>();
                        Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
                        String attributeId = updateFieldMap.get("value");
                        AttributeDefinition resolvedAttrib = entityDefinition.get().getAttribute(attributeId.toString());
                        if (resolvedAttrib != null) {
                            col.add(resolvedAttrib.getDisplayName());
                        }
                        Map<String, String> newValueMap = new HashMap<>(s.get("newValue"));
                        col.add(newValueMap.get("value"));
                        res.add(Pair.of(configProperty + "@@@" + i, col.toString()));
                        i++;
                    }
                    return res;
                }
            }
        }
        return super.toUserFriendlyValue(context, configProperty);
    }

    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {

        BatchActionContext batchActionContext = context.getBatchActionContext();
        final boolean batchModeParamSet = actionConfig.containsKey("batchMode");
        //If batchMode param is set, use the set value, otherwise default to false, for backward compatibility
        boolean batchMode = batchModeParamSet ? BooleanType.VALUE.convert(actionConfig.get("batchMode")) : false;
        //if not batch mode, and not being called to finalize batch actions, create an record
        String syncariEntityDefId = getConfig("syncariEntityDefId", actionConfig);
        EntityDefinition syncariEntity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));

        if (!batchMode && !batchActionContext.shouldRunActions()) {
            Pair<EntityData, TransactionLog> entityData = createInsertRecord(context, actionConfig);
            EntityData saved = entityData.getX();
            if (!entityData.getX().getValues().isEmpty()) {
                saved = entityRepoService.save(syncariEntity, entityData.getX());
                var savedTxnLog = transactionLogService.log(entityData.getY());
                // save the correct txn log id
                entityRepoService.updateLastTransactionId(syncariEntity, List.of(savedTxnLog), List.of(saved));
            }
            log.debug("insertsyncariRecords:", saved);
            context.put("Record from " + context.getCurrentNode().getName(), saved);
            return new ActionResult(true, saved);
        }
        if (batchMode && batchActionContext.shouldRunActions()) {
            List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
            if (collectedParams != null && !collectedParams.isEmpty()) {

                ForkJoinPool customForkJoinPool = new ForkJoinPool(4);
                List<Pair<EntityData,TransactionLog>> entityData = collectedParams.stream().map(param -> {
                            Map<String, Object> currentParam = (Map<String, Object>) param;
                            return (Pair<EntityData, TransactionLog>) currentParam.get("record");
                        }).collect(Collectors.toList());

                Instance instance = SyncariContext.getInstance();
                Organization organization = SyncariContext.getOrganziation();
                User user = SyncariContext.getUser();
                customForkJoinPool.submit(() -> {
                    ListUtils.partition(entityData, 500).parallelStream().forEach(data -> {
                        SyncariContext.runWithContext(organization, instance, user, () -> {
                            List<EntityData> savedRecords = entityRepoService.saveAll(syncariEntity, data.stream().map(Pair::getX).collect(Collectors.toList()));
                            List<TransactionLog> savedLogs = transactionLogService.log(data.stream().map(Pair::getY).collect(Collectors.toList()));
                            entityRepoService.updateLastTransactionId(syncariEntity, savedLogs, savedRecords);
                        });
                    });
                }).join();
                customForkJoinPool.shutdown();
            }
            return new ActionResult(true);

        } else {
            Pair<EntityData, TransactionLog> entityData = createInsertRecord(context, actionConfig);
            var updatedParams = new HashMap<>(actionConfig.getConfigMap());
            updatedParams.put("record", entityData);
            batchActionContext.updateBatchContext(context.getCurrentNode(), updatedParams);
            return new ActionResult(true, entityData.getX());
        }
    }

    private Pair<EntityData, TransactionLog> createInsertRecord(GraphContext context, GenericActionConfig actionConfig) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", actionConfig);
        EntityDefinition syncariEntity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        List<Update> updates = toChangeSet(syncariEntity, context, actionConfig);
        long createdAt = Instant.now().toEpochMilli();
        String id = ObjectId.get().toHexString();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityData entity = (EntityData) context.get("record");
        EntityData record = new EntityData(syncariEntity.getApiName()).setCreatedAt(createdAt).setLastModified(createdAt).setNew(true).setId(id).setSyncariEntityId(id);
        TransactionLog transactionLog = new TransactionLog().setOperation(Operation.create)
                .setEntityName(syncariEntity.getApiName())
                .setEntityId(syncariEntity.getId())
                .setOccurredAt(System.currentTimeMillis())
                .setSyncariId(record.getSyncariEntityId())
                .addSource(syncariConnector.getId(), syncariConnector.getName(), syncariEntityDefId, entity != null ? entity.getSyncariEntityId() : "", System.currentTimeMillis())
                .setAdditionalInfo(Map.of("notes", String.format("Updated by pipeline %s", context.getGraph().getName()), "graphId", context.getGraph().getId()));
        updates.forEach(update -> {
            transactionLog.addChange(new FieldChange().setFieldId(update.getAttributeId()).setApiName(update.getApiName())
                    .setOldValue(record.getValue(update.getApiName())).setNewValue(update.getNewValue()));
            record.addValue(update.getApiName(), update.getNewValue());
        });

        return Pair.of(record, transactionLog);
    }

    private List<Update> toChangeSet(EntityDefinition syncariEntity, GraphContext context, GenericActionConfig actionConfig) {
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) actionConfig.getConfigMap()
                .getOrDefault("insertFields", List.of());
        List<Update> updates = new ArrayList<>();
        for (Map<String, Map<String, String>> s : sortFields) {
            String attributeId = s.get("updateField").get("value");
            String newValue = s.get("newValue").get("value");
            AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
            updates.add(new Update(attributeId, attribute.getApiName(), syncariEntity.getAttribute(attributeId).convert(tokenHelper.resolveTokensObject(context, newValue)), "replace"));
        }
        return updates;
    }
    
    @Override
    public boolean postProcess(QuickStartContext context) {
      PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
      SharableNode sharableNode = context.getCurrentNode();
      SharableActionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
      Map<String, Object> configMap = functionNodeConfig.getConfigMap();
      
      List<Map<String, Map<String, String>>> insertFields = (List<Map<String, Map<String, String>>>) configMap.get("insertFields");
      for (Map<String, Map<String, String>> s : insertFields) {
          Map<String, String> updateFieldMap = new HashMap<>(s.get("updateField"));
          String attributeId = updateFieldMap.get("value");
          AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
          if (resolvedAttrib != null) {
              updateFieldMap.put("value", resolvedAttrib.getId());
          }
          s.put("updateField", updateFieldMap);

          Map<String, String> newValueMap = new HashMap<>(s.get("newValue"));
          String newValue = newValueMap.get("value");
          if (TokenHelper.hasTokens(newValue)) {
              var resolvedValue = (String) qsConfig.getResolvedValueByType(newValue, QSDependency.Type.Token);
              if (resolvedValue != null) {
                  newValueMap.put("value", resolvedValue);
              }
          }
          s.put("newValue", newValueMap);
      }
      GenericActionConfig nodeConfig = context.getCurrentMappingNode().getTypedConfiguration();
      var gacMap = nodeConfig.getConfigMap();
      gacMap.put("insertFields", insertFields);
      nodeConfig.setConfigMap(gacMap);
      return true;
    }

}
