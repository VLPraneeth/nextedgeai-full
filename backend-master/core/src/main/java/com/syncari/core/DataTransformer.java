package com.syncari.core;

import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.HttpSourceConfigInfo;
import com.syncari.connector.Status;
import com.syncari.connector.WebhookConfigInfo;
import com.syncari.connector.custom.CloudFunctionInfo;
import com.syncari.connector.data.*;
import com.syncari.core.config.AppConfig;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.http.source.HttpSourceConfig;
import com.syncari.core.model.*;
import com.syncari.core.model.EventData;
import com.syncari.core.model.misc.Partition;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.misc.Watermark;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.EntityType;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.SchemaService;
import com.syncari.core.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DataTransformer {

	@Autowired
	TagService tagService;

	@Autowired
	ConnectorMetadataService connectorMetaService;

	@Autowired
	SchemaService schemaService;

	@Autowired
	AppConfig appConfig;

	public EntitySchema toEntitySchema(EntityDefinition def, Connector connector) {
		return toEntitySchema(def, connector, new HashMap<>());
	}

	private EntitySchema toEntitySchema(EntityDefinition def, Connector connector, Map<String, EntitySchema> transformed) {
		EntitySchema schema = new EntitySchema(def.getApiName(), def.getDisplayName());
		schema.setPluralName(def.getPluralName());
		schema.setId(def.getId());
		schema.setDescription(def.getDescription());
		schema.setCustom(def.isCustom());
		schema.setReadOnly(def.isReadOnly());
		schema.setCustom(def.isCustom());
		//add this to cache before processing child attributes,
		//to break loops
		transformed.put(schema.getApiName(), schema);

		for (AttributeDefinition f : def.getAttributes()) {
			AttributeSchema attr = toAttrSchema(f, def, connector, transformed);
			schema.addField(attr);
		}
		schema.setAdditionalProperties(def.getAdditionalProperties());
		return schema;
	}

	public List<EntitySchema> toEntitySchema(List<EntityDefinition> def, Connector connector) {
		return def.stream().map(d -> toEntitySchema(d, connector)).collect(Collectors.toList());
	}

	public EntityDefinition toEntityDefinition(EntitySchema def, Connector connector) {
		EntityDefinition schema = new EntityDefinition(def.getApiName(), def.getDisplayName());
		schema.setPluralName(def.getPluralName());
		schema.setId(def.getId());
		schema.setReadOnly(def.isReadOnly());
		schema.setConnectorId(connector.getId());
		schema.setConnectorTypeId(connector.getMetadataId());
		schema.setDescription(def.getDescription());
		schema.setPartitions(def.getPartitions().stream()
				.map(p -> new Partition(p.getId(), p.getName(), p.getCreatedOn(), p.getRowCount(), p.getColumnCount()))
				.collect(Collectors.toSet()));
		schema.setCustom(def.isCustom());
		schema.setChild(def.isChild());
		schema.setStatus(com.syncari.core.model.util.Status.ACTIVE);
		for (AttributeSchema f : def.getAttributes()) {
			AttributeDefinition attr = toAttributeDefinition(f);
			schema.addField(attr);
		}
		schema.setSourceParams(
				def.getSourceParams().stream().map(p -> toAttributeDefinition(p)).collect(Collectors.toList()));
		schema.setDestinationParams(
				def.getDestParams().stream().map(p -> toAttributeDefinition(p)).collect(Collectors.toList()));
		schema.setAdditionalProperties(def.getAdditionalProperties());
		return schema;
	}

	public List<EntityDefinition> toEntityDefinition(List<EntitySchema> def, Connector connector) {
		return def.stream().map(d -> toEntityDefinition(d, connector)).collect(Collectors.toList());
	}

	
	public EntitySchema toEntitySchema(EntityDef def, Connector connector) {
		EntitySchema schema = new EntitySchema(def.getApiName(), def.getDisplayName());
		schema.setId(def.getId());
		schema.setReadOnly(def.isReadonly());
		for (AttributeDef f : def.getFields()) {
			AttributeSchema attr = toAttrSchema(f, connector);
			schema.getAttributes().add(attr);
		}
		return schema;
	}

	public List<EntitySchema> toEntitySchemaFrom(List<EntityDef> def, Connector connector) {
		return def.stream().map(d -> toEntitySchema(d, connector)).collect(Collectors.toList());
	}

	public AttributeSchema toAttrSchema(AttributeDefinition f, EntityDefinition entityDefintion, Connector connector) {
		return toAttrSchema(f, entityDefintion, connector, new HashMap<>());
	}

	public AttributeSchema toAttrSchema(AttributeDefinition f, EntityDefinition entityDefintion, Connector connector, Map<String, EntitySchema> transformed) {
		AttributeSchema attr = new AttributeSchema();
		attr.setId(f.getId());
		attr.setApiName(f.getApiName());
		attr.setDisplayName(f.getDisplayName());
		attr.setDescription(f.getDescription());
		attr.setDataType(f.getDataType().getName());
		attr.setCustom(f.isCustom());
		if (f.getDefaultValue() != null) {
			attr.setDefaultValue(f.getDefaultValue().toString());
		}
		attr.setCalculated(f.isCalculated());
		attr.setNillable(f.isNillable());
		attr.setUnique(f.isUnique());
		attr.setUpdateable(f.isUpdatable());
		attr.setCreateOnly(f.isCreateOnly());
		attr.setPicklistValues(f.getPicklistValues());
		attr.setPicklist(f.getPicklist() != null ? f.getPicklist().stream().map(p -> new AttributeSchema.Picklist().setId(p.getId()).setLabel(p.getLabel()))
				.collect(Collectors.toList()) : null);
		attr.setLength(f.getLength());
		attr.setPrecision(f.getPrecision());
		attr.setScale(f.getScale());
		attr.setReferenceTo(f.getReferenceTo());
		attr.setStatus(f.getStatus() == null ? null : Status.valueOf(f.getStatus().name()));
		attr.setReferenceTargetField(f.getReferenceTargetField());
		attr.setReferenceToPluralName(f.getReferenceToPluralName());
		attr.setSystem(f.isSystem());
		attr.setMultiValueField(f.isMultiValueField());
		attr.setSyncariDefined(f.isSyncariDefined());
		attr.setParentAttributeId(f.getParentAttributeId());
		attr.setSubDataType(f.getSubDataType() != null ? f.getSubDataType().getName() : null);
		attr.setIndex(f.getIndex());
		if (f.isIdField() || (connector.getMetadata().getIdFieldName() != null
				&& connector.getMetadata().getIdFieldName().equalsIgnoreCase(f.getApiName()))) {
			attr.setIdField(true);
		}
		if(StringUtils.isNotEmpty(f.getCompositeKey())){
			attr.setCompositeKey(f.getCompositeKey());
		}

		boolean isDefaultWatermarkField =isDefaultWatermarkField(f, entityDefintion, connector);

		if (f.isWatermarkField() ||isDefaultWatermarkField) {
			attr.setWatermarkField(true);
		}
		attr.setNoTimezoneWatermark(f.isNoTimezoneWatermark());
		if (f.isCreatedAtField() || (connector.getMetadata().getCreatedAtFieldName() != null
				&& connector.getMetadata().getCreatedAtFieldName().equalsIgnoreCase(f.getApiName()))) {
			attr.setCreatedAtField(true);
		}
		if (f.isUpdatedAtField() || (connector.getMetadata().getUpdatedAtFieldName() != null
				&& connector.getMetadata().getUpdatedAtFieldName().equalsIgnoreCase(f.getApiName()))) {
			attr.setUpdatedAtField(true);
		}
		if (f.isChild() && !StringUtils.isBlank(f.getReferenceTo())) {
			if (transformed.containsKey(f.getReferenceTo())) {
				attr.setChildSchema(transformed.get(f.getReferenceTo()));
			} else {
				schemaService.findEntity(entityDefintion.getConnectorId(), f.getReferenceTo()).ifPresent(entity -> {
					final EntitySchema childSchema = toEntitySchema(entity, connector, transformed);
					attr.setChildSchema(childSchema);
					transformed.put(f.getReferenceTo(), childSchema);
				});
			}
		}
		return attr;
	}

	private boolean isDefaultWatermarkField(AttributeDefinition f, EntityDefinition entityDefintion, Connector connector) {
		return connector.getMetadata().isDefaultWatermarkField(entityDefintion==null ? null: entityDefintion.getApiName(), f.getApiName());
	}

	public AttributeSchema toAttrSchema(AttributeDef f, Connector connector) {
		AttributeSchema attr = new AttributeSchema();
		attr.setId(f.getId());
		attr.setApiName(f.getApiName());
		attr.setDisplayName(f.getDisplayName());
		attr.setDataType(f.getDataType());
		attr.setDescription(f.getDescription());
		attr.setIdField(f.isIdField());
		if(StringUtils.isNotEmpty(f.getCompositeKey())){
			attr.setCompositeKey(f.getCompositeKey());
		}
		attr.setMultiValueField(f.isMultiValueField());
		attr.setSyncariDefined(f.getIsSyncariDefined());
		attr.setParentAttributeId(f.getParentAttributeId());
		attr.setLength(f.getLength());
		attr.setPrecision(f.getPrecision());
		attr.setScale(f.getScale());
		attr.setSubDataType(f.getSubDataType());
		attr.setIndex(f.getIndex());
		return attr;
	}

	public AttributeDefinition toAttributeDefinition(AttributeSchema f) {
		AttributeDefinition attr = new AttributeDefinition();
		attr.setId(f.getId());
		attr.setApiName(f.getApiName());
		attr.setDisplayName(f.getDisplayName());
		attr.setDescription(f.getDescription());
		attr.setDataType(DatatypeFactory.getDatatype(f.getDataType()));
		attr.setCustom(f.isCustom());
		if (f.getDefaultValue() != null) {
			attr.setDefaultValue(f.getDefaultValue().toString());
		}
		attr.setCalculated(f.isCalculated());
		attr.setIdField(f.isIdField());
		if (StringUtils.isNotEmpty(f.getCompositeKey())){
			attr.setCompositeKey(f.getCompositeKey());
		}
		attr.setNillable(f.isNillable());
		attr.setUnique(f.isUnique());
		attr.setUpdatable(f.isUpdateable());
		attr.setCreateOnly(f.isCreateOnly());
		attr.setLength(f.getLength());
        attr.setPrecision(f.getPrecision());
        attr.setScale(f.getScale());
        attr.setReferenceTo(f.getReferenceTo());
        attr.setReferenceTargetField(f.getReferenceTargetField());
        attr.setReferenceToPluralName(f.getReferenceToPluralName());
		attr.setPicklistValues(f.getPicklistValues());
		attr.setPicklist(f.getPicklist() != null ? f.getPicklist().stream().map(p -> new AttributeDefinition.Picklist().setId(p.getId()).setLabel(p.getLabel()))
				.collect(Collectors.toList()) : null);
		attr.setDefaultValue(f.getDefaultValue());
		attr.setWatermarkField(f.isWatermarkField());
		attr.setNoTimezoneWatermark(f.isNoTimezoneWatermark());
		attr.setSystem(f.isSystem());
		attr.setMultiValueField(f.isMultiValueField());
		attr.setSyncariDefined(f.isSyncariDefined());
		attr.setParentAttributeId(f.getParentAttributeId());
		attr.setStatus(com.syncari.core.model.util.Status.ACTIVE);
		attr.setSubDataType(StringUtils.isNotBlank(f.getSubDataType()) ? DatatypeFactory.getDatatype(f.getSubDataType()) : null);
		attr.setIndex(f.getIndex());
		return attr;
	}

	public ConnectorInfo toConnectorInfo(Connector connector) {
		ConnectorInfo info = new ConnectorInfo(connector.getId(), connector.getName(), connector.getEndpoint(),
				SyncariContext.getOrganziation() == null ? "" : SyncariContext.getSyncariId());
		if ((null != connector) && (null != connector.getAuthConfig())){
			info.setAuthConfig(connector.getAuthConfig().clone());
		}
		info.setMetaConfig(new HashMap<>(connector.getMetaConfig()));
		info.setDatastoreType(connector.getDatastoreType());
        if (connector.getMetadata() == null && StringUtils.isNotBlank(connector.getMetadataId())) {
            Optional<ConnectorMetadata> metadata = connectorMetaService.findById(connector.getMetadataId());
            metadata.ifPresent(m -> {
                connector.setMetadata(m);
                if (connector.getDailyQuota() == 0) {
                    connector.setDailyQuota(m.getDefaultApiLimit());
                }
            });
        }
        if (connector.getMetadata() != null) {
            info.setCustom(connector.getMetadata().isCustom());
            if (connector.getMetadata().isCustom()) {
                CloudFunctionInfo cfInfo = connectorMetaService.getCloudFunctionInfo(connector.getMetadata());
                info.setCloudFunctionInfo(cfInfo);
            }
			info.setConnectorMetadataName(connector.getMetadata().getName());
			info.setApiMaxCrudSize(connector.getMetadata().getApiMaxCrudSize());
			if(connector.getMetadata().isWebhook()) {
			  info.setWebhookConfig(toWebhookConfigInfo(connector.getMetadata()));
			}
			if(connector.getMetadata().isHttpSource() && CollectionUtils.isNotEmpty(connector.getMetadata().getHttpSources())) {
			  info.setHttpSourceConfig(connector.getMetadata().getHttpSources().stream().map(src -> toHttpConfigInfo(src)).collect(Collectors.toList()));
			}
        }
		if (connector.getSetting() != null) {
			info.setInternalConfig(connector.getSetting().getInternalConfig());
		}
		info.setAuthType(connector.getAuthType());
		return info;
	}

	public WatermarkInfo toWatermarkInfo(Watermark w) {
		WatermarkInfo info = new WatermarkInfo(w.getStart(), w.getEnd(), w.isInitial(), w.getOffset());
		info.setLimit((int) w.getLimit());
		info.setResync(w.isResync());
		info.setPartialResync(w.isPartialResync());
		info.setChangeStream(w.getChangeStream());
		if (w.getStreamState() != null) {
			info.setStreamState(new StreamState().setLastModified(w.getStreamState().getLastModified())
					.setPreviousCursor(w.getStreamState().getPreviousCursor())
					.setOffsetOverflow(w.getStreamState().isOffsetOverflow())
			);
		}
		if (w.getPruneState() != null && w.getPruneState().isPruned()) {
			info.setPruneState(new PruneState(w.getPruneState().getLastRecordNotPruned(), w.getPruneState().getOffset(), w.getPruneState().isPruned(), w.getPruneState().getTimestamp()));
		}
		return info;
	}

	public EntityDefinition toEntityDefinition(EntityDef def) {
		EntityDefinition schema = new EntityDefinition(def.getApiName(), def.getDisplayName());
		schema.setId(def.getId());
		schema.setReadOnly(def.isReadonly());
		schema.setDataStoreName(def.getDataStoreName());
		schema.setDescription(def.getDescription());
		schema.setDraftStatus(def.getDraftStatus() == null ? DraftStatus.NEW : def.getDraftStatus());
		schema.setCustom(EntityType.custom.equals(def.getType()));
		schema.setStatus(def.getStatus());
		schema.setChild(def.isChild());
		schema.setRunDFI(def.isRunDFI());
		schema.setRunMerge(def.isRunMerge());
//		schema.setDatasetId(def.getDatasetId());
		var tags = def.getTags().stream()
				.map(t -> new Tag(t, true, Taggable.entity, def.getId()))
				.collect(Collectors.toList());
		schema.setTags(tags);

		for (AttributeDef f : def.getFields()) {
			AttributeDefinition attr = toAttributeDefinition(f);
			schema.getAttributes().add(attr);
		}
		return schema;
	}

	public AttributeDefinition toAttributeDefinition(AttributeDef f) {
		AttributeDefinition attr = new AttributeDefinition();
		attr.setId(f.getId());
		attr.setApiName(f.getApiName());
		attr.setDisplayName(f.getDisplayName());
		attr.setDataStoreName(f.getDataStoreName());
		attr.setDescription(f.getDescription());
		attr.setDataType(DatatypeFactory.getDatatype(f.getDataType()));
		attr.setCustom(EntityType.custom.equals(f.getType()));
		attr.setIdField(f.isIdField());
		if (StringUtils.isNotEmpty(f.getCompositeKey())){
			attr.setCompositeKey(f.getCompositeKey());
		}
		attr.setMultiValueField(f.isMultiValueField());
		attr.setPicklistValues(f.getValues());
		attr.setDraftStatus(f.getDraftStatus() == null ? DraftStatus.NEW : f.getDraftStatus());
		attr.setStatus(f.getStatus());
		attr.setLength(f.getLength());
		attr.setScale(f.getScale());
		attr.setPrecision(f.getPrecision());
		attr.setNillable(!f.isRequired());
		attr.setUpdatable(!f.isReadOnly());
		attr.setCreateOnly(f.isCreateOnly());
		attr.setUnique(f.isUnique());
		attr.setWatermarkField(f.isWatermarkField());
		if ((StringUtils.isNotEmpty(f.getDataType())) && ((f.getDataType().equalsIgnoreCase("reference")) || (f.getDataType().equalsIgnoreCase("externalId")))){
			attr.setReferenceTo(f.getReferenceTo());
			attr.setReferenceTargetField(f.getReferenceTargetField());
		}
		attr.setSyncariDefined(f.getIsSyncariDefined());
		attr.setParentAttributeId(f.getParentAttributeId());
		var tags = f.getTags().stream()
				.map(t -> new Tag(t, true, Taggable.attribute, f.getId()))
				.collect(Collectors.toList());
		attr.setTags(tags);
		attr.setSubDataType(StringUtils.isNotBlank(f.getSubDataType()) ? DatatypeFactory.getDatatype(f.getSubDataType()) : null);
		attr.setIndex(f.getIndex());
		return attr;
	}

	public EntityDef toEntityDef(EntityDefinition def){
		EntityDef entityDef = new EntityDef(def.getId(), def.getApiName());
		entityDef.setDescription(def.getDescription());
		entityDef.setDisplayName(def.getDisplayName());
		entityDef.setDataStoreName(def.getDataStoreName());
		entityDef.setType(def.isCustom() ? EntityType.custom : EntityType.standard);
		entityDef.setDraftStatus(def.getDraftStatus());
		entityDef.setCreatedAt(def.getCreatedAt());
		entityDef.setUpdatedAt(def.getUpdatedAt());
		entityDef.setCreatedBy(def.getCreatedBy());
		entityDef.setUpdatedBy(def.getUpdatedBy());
		entityDef.setStatus(def.getStatus());
		entityDef.setReadonly(def.isReadOnly());
		entityDef.setChild(def.isChild());
		entityDef.setRunDFI(def.isRunDFI());
		entityDef.setRunMerge(def.isRunMerge());
		entityDef.setTags(tagService.getTagNames(Taggable.entity, def.getId()));
		var fields = def.getAttributes().stream().map(field -> toAttributeDef(field)).collect(Collectors.toList());
		entityDef.setFields(fields);
//		entityDef.setDatasetId(def.getDatasetId());

		return entityDef;
	}

	public AttributeDef toAttributeDef(AttributeDefinition field){
		AttributeDef attrDef = new AttributeDef(field.getId(), field.getApiName())
				.setDisplayName(field.getDisplayName())
				.setDescription(field.getDescription())
				.setDataType(field.getDataType().getName())
				.setValues(field.getPicklistValues())
				.setIdField(field.isIdField())
				.setStatus(field.getStatus())
				.setDraftStatus(field.getDraftStatus())
				.setRequired(!field.isNillable())
				.setReadOnly(!field.isUpdatable())
				.setCreateOnly(field.isCreateOnly())
				.setLength(field.getLength())
				.setPrecision(field.getPrecision())
				.setScale(field.getScale())
				.setUnique(field.isUnique())
				.setSystem(field.isSystem())
				.setSchemaUpdatable(field.isSchemaUpdatable())
				.setSchemaDeletable(field.isSchemaDeletable())
				.setWatermarkField(field.isWatermarkField())
				.setReferenceTo(field.getReferenceTo())
				.setReferenceTargetField(field.getReferenceTargetField())
				.setSyncariDefined(field.isSyncariDefined())
				.setParentAttributeId(field.getParentAttributeId())
				.setMultiValueField(field.isMultiValueField())
				.setSubDataType(field.getSubDataType() != null ? field.getSubDataType().getName() : null)
				.setIndex(field.getIndex())
				;
		if (StringUtils.isNotEmpty(field.getCompositeKey())){
			attrDef.setCompositeKey(attrDef.getCompositeKey());
		}
        return attrDef;
	}
	
	public List<EventData> toEventData(List<com.syncari.connector.data.EventData> data) {
		List<EventData> results = new ArrayList<>();
        for (com.syncari.connector.data.EventData d : data) {
        	EventData record = new EventData().setOperation(d.getOperation()).setData(d.getData()).setEventId(d.getEventId());
        	record.setConnectorId(d.getData().getConnectorId());
            results.add(record);
        }
        return results;
	}
	
	public HttpSourceConfigInfo toHttpConfigInfo(HttpSourceConfig other) {
		var info = new HttpSourceConfigInfo();
		info.setApiName(other.getApiName());
		info.setDisplayName(other.getDisplayName());
		info.setDescription(other.getDescription());
		info.setEndpoint(other.getEndpoint());
		info.setMethod(other.getMethod());
		info.setBody(other.getBody());
		info.setSchema(other.getSchema());
		info.setHeaders(other.getHeaders());
		info.setVariables(other.getVariables());
		info.setRecordSelector(other.getRecordSelector());
		info.setIdSelector(other.getIdSelector());
		info.setWmSelector(other.getWmSelector());
		info.setCreatedAtSelector(other.getCreatedAtSelector());
		info.setDeletedFlagSelector(other.getDeletedFlagSelector());
		info.setCreatedBySelector(other.getCreatedBySelector());
		info.setModifiedBySelector(other.getModifiedBySelector());
		info.setType(other.getType());
		info.setLimitParam(other.getLimitParam());
		info.setOffsetParam(other.getOffsetParam());
		info.setPageNumberParam(other.getPageNumberParam());
		info.setPageSizeParam(other.getPageSizeParam());
		info.setLimitValue(other.getLimitValue());
		info.setOffsetValue(other.getOffsetValue());
		info.setPageNumberValue(other.getPageNumberValue());
		info.setPageSize(other.getPageSize());
		info.setCursorType(other.getCursorType());
		info.setNextCursorSelector(other.getNextCursorSelector());
		info.setNextCursorParam(other.getNextCursorParam());
		info.setStartValue(other.getStartValue());
		return info;
		
    }
	
	public WebhookConfigInfo toWebhookConfigInfo(ConnectorMetadata other) {
      var info = new WebhookConfigInfo();
      info.setRecordSelector(other.getRecordSelector());
      info.setIdSelector(other.getIdSelector());
      info.setSchema(other.getSchema());
      return info;
      
  }
}
