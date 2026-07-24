package com.syncari.core.functions;

import com.syncari.connector.service.def.FileService;
import com.syncari.core.DataTransformer;
import com.syncari.core.actions.ActionConstants;
import com.syncari.core.actions.DefaultAction;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.sharable.SharableActionNodeConfig;
import com.syncari.core.model.misc.sharable.SharableNode;
import com.syncari.core.model.util.ErrorCode;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.quickstart.v2.PipelineQSConfig;
import com.syncari.core.quickstart.v2.QSDependency;
import com.syncari.core.quickstart.v2.QuickStartContext;
import com.syncari.core.quickstart.v2.dependency.DependencyUtil;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.service.DataServiceFactory;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.utils.MongoCriteria;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(ActionConstants.EXPORT_SYNCARI_RECORDS)
@Slf4j
public class ExportSyncariRecordsAction extends DefaultAction {
    public static final String SYNCARI_ENTITY_DEF_ID = "syncariEntityDefId";
    public static final String EXPORT_FIELDS = "exportFields";
    public static final String FILE_NAME = "fileName";
    public static final String DATE_FORMAT = "dateFormat";
    public static final String DATETIME_FORMAT = "dateTimeFormat";
    public static final String FOLDER = "folder";
    public static final String USE_DISPLAY_NAME = "useDisplayName";
    public static final String MAX_RECORDS = "maxRecords";
    public static final String STORAGE_SYNAPSE_ID = "storageSynapseId";
    public static final String TOTAL_EXPORT_COUNT = "totalExportCount";

    @Autowired
    EntityRepo entityRepo;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    DataServiceFactory dataServiceFactory;
    @Autowired
    DataTransformer dataTransformer;

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
        final Optional<ValidationError> dtfError = validateDateFormat(DATETIME_FORMAT, node, configMap, configNameLabelMap);
        dtfError.ifPresent(e -> errors.add(e));
        final Optional<ValidationError> dfError = validateDateFormat(DATE_FORMAT, node, configMap, configNameLabelMap);
        dfError.ifPresent(e -> errors.add(e));

        var syncariEntityDefId = configMap.get(SYNCARI_ENTITY_DEF_ID);
        if (syncariEntityDefId == null) {
            return errors;
        }
        Optional<EntityDefinition> syncariEntityMaybe = schemaService.findEntity(syncariEntityDefId.toString());
        validateCondition(ValidationError.scopedError(node.getScope(), node.getId()), syncariEntityMaybe.isEmpty(),
                i18n("invalid_config_in_node", configNameLabelMap.get(SYNCARI_ENTITY_DEF_ID), syncariEntityDefId,
                        node.getName(), graph.getName()), ErrorCode.E1106.getCode()).ifPresent(e -> errors.add(e));


        syncariEntityMaybe.ifPresent(syncariEntity -> validationContext.getData().put("syncariEntity", syncariEntity));
        // inputFieldId refers to attribute of connected sources or core entity
        // validate each field
        return errors;
    }

    private Optional<ValidationError> validateDateFormat(String configKey, MappingNode node, Map<String, Object> config, Map<String, String> configNameLabelMap) {
        return Optional.ofNullable(config.get(configKey)).flatMap(df -> {
            if (!StringUtils.isEmpty(tokenHelper.resolveTokens(Map.of(), df.toString()).getX())) {
                try {
                    new SimpleDateFormat(df.toString()).format(new Date());
                } catch (Exception e) {
                    String configLabel = i18n(configNameLabelMap.get(configKey));
                    return Optional.of(ValidationError
                            .scopedError(node.getScope(), node.getId())
                            .setMessage(i18n("config_validation_error", configLabel, e.getMessage())));
                }
            }
            return Optional.empty();
        });
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
        List<String> exportFields = (List<String>) configMap.get(EXPORT_FIELDS);
        for (String attributeId : exportFields) {
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

        // 2. attributes from insertFields config. Moved to post proess
        actionNodeConfig.setConfigMap(configMap);
        sharableNode.setConfiguration(actionNodeConfig);
        return sharableGraphTransformer.toMappingNode(sharableNode, context.getCurrentPipeline());
    }

    @Override
    public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
        if (context != null && context.getCurrentNode() != null) {
            if (EXPORT_FIELDS.equals(configProperty)) {
                List<Pair<String, String>> res = new ArrayList<Pair<String, String>>();
                GenericActionConfig actionConfig = context.getCurrentNode().getTypedConfiguration();
                Map<String, Object> configMap = actionConfig.getConfigMap();
                var syncariEntityDefId = configMap.getOrDefault(SYNCARI_ENTITY_DEF_ID, configProperty);
                Optional<EntityDefinition> entityDefinition = schemaService.getSyncariEntityById(String.valueOf(syncariEntityDefId));
                if (entityDefinition.isPresent()) {
                    List<String> exportFields = (List<String>) configMap.get(configProperty);
                    List<String> col = new ArrayList<>();
                    for (String attributeId : exportFields) {
                        AttributeDefinition resolvedAttrib = entityDefinition.get().getAttribute(attributeId);
                        if (resolvedAttrib != null) {
                            col.add(resolvedAttrib.getDisplayName());
                        }
                    }
                    res.add(Pair.of(configProperty, col.toString()));
                    return res;
                }
            }
        }
        return super.toUserFriendlyValue(context, configProperty);
    }

    public ActionResult execute(GenericActionConfig actionConfig, GraphContext context) {
        String syncariEntityDefId = getConfig(SYNCARI_ENTITY_DEF_ID, actionConfig);
        String fileName = tokenHelper.resolveTokens(context, getConfig(FILE_NAME, actionConfig));
        String folder = tokenHelper.resolveTokens(context, getConfig(FOLDER, actionConfig));
        String dateFormat = tokenHelper.resolveTokens(context, getConfig(DATE_FORMAT, actionConfig));
        String dateTimeFormat = tokenHelper.resolveTokens(context, getConfig(DATETIME_FORMAT, actionConfig));
        EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        long maxRecords = getConfigOrDefault(MAX_RECORDS, actionConfig, IntegerType.VALUE, (long) Integer.MAX_VALUE);
        boolean useDisplayNameAsHeader = getConfigOrDefault(USE_DISPLAY_NAME, actionConfig, BooleanType.VALUE, false);
        final List<AttributeDefinition> selectedAttributes = getSelectedAttributes(actionConfig, entity);
        //Supports S3,SFTP, Google Drive right now
        String storageSynapseId = getConfig(STORAGE_SYNAPSE_ID, actionConfig);
        Map<String, Object> predicates = getConfig(PREDICATE, actionConfig);
        final Optional<Connector> maybeConnector = connectorService.find(storageSynapseId);
        return maybeConnector.map(connector -> {
            final FileService fileService = dataServiceFactory.getFileService(connector.getMetadata());
            Optional<MongoCriteria> mongoCriteria = getCriteria(context, entity, predicates, this.entityRepo);
            RecordInputStream recordInputStream = new RecordInputStream(entityRepo, entity, selectedAttributes, mongoCriteria, (int) maxRecords, useDisplayNameAsHeader, dateFormat, dateTimeFormat);
            fileService.writeFile(dataTransformer.toConnectorInfo(connector), recordInputStream, fileName, folder);
            return new ActionResult(true, Map.of(TOTAL_EXPORT_COUNT, recordInputStream.getTotalRecordsRead()));
        }).orElse(new ActionResult(true, Map.of(TOTAL_EXPORT_COUNT, 0)));
    }


    private List<AttributeDefinition> getSelectedAttributes(GenericActionConfig actionConfig, EntityDefinition entity) {
        List<String> selectedAttributeIds = getMultiValuedConfig(EXPORT_FIELDS, actionConfig, StringType.VALUE);
        Set<String> selectedAttributeIdSet = new HashSet<>(selectedAttributeIds);
        if (!selectedAttributeIdSet.isEmpty()) {
            return entity.getAttributes().stream()
                    .filter(a -> selectedAttributeIdSet.contains(a.getId()))
                    .collect(Collectors.toList());
        }
        return entity.getAttributes();
    }
    
    @Override
    public boolean postProcess(QuickStartContext context) {
      PipelineQSConfig qsConfig = (PipelineQSConfig) context.getQsConfig();
      SharableNode sharableNode = context.getCurrentNode();
      SharableActionNodeConfig functionNodeConfig = sharableNode.getTypedConfiguration();
      Map<String, Object> configMap = functionNodeConfig.getConfigMap();
      
      List<String> exportFields = (List<String>) configMap.get(EXPORT_FIELDS);
      List<String> targetExportFields = new ArrayList<>();
      for (String attributeId : exportFields) {
          AttributeDefinition resolvedAttrib = (AttributeDefinition) qsConfig.getResolvedValueByType(attributeId, QSDependency.Type.Attribute);
          if (resolvedAttrib != null) {
              targetExportFields.add(resolvedAttrib.getId());
          }

      }
      GenericActionConfig nodeConfig = context.getCurrentMappingNode().getTypedConfiguration();
      var gacMap = nodeConfig.getConfigMap();
      gacMap.put(EXPORT_FIELDS, targetExportFields);
      nodeConfig.setConfigMap(gacMap);
      return true;
    }

}