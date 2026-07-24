package com.syncari.karibu.rest.util;

import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.ConnectorMetadata;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.pipeline.expression.Not;
import com.syncari.core.schema.AttributeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.schema.EntityType;
import com.syncari.core.service.ConnectorMetadataService;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.request.CreateSyncariEntityRequest;
import com.syncari.karibu.rest.request.FieldRequest;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Component
public class SchemaUtils {

    @Autowired
    SchemaService schemaService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    ConnectorMetadataService connectorMetadataService;


    public EntityDefinition convertSyncariCreateEntityRequest(CreateSyncariEntityRequest request) {
        EntityDefinition schema = new EntityDefinition(request.getApiName(), request.getDisplayName());
        schema.setReadOnly(request.isReadonly());
        schema.setDataStoreName(request.getDataStoreName());
        schema.setDescription(request.getDescription());
        schema.setCustom(EntityType.custom.equals(request.getType()));
        schema.setStatus(request.getStatus());
        schema.setChild(request.isChild());
        // entity id is not created, it is assigned after creating entity
        var tags = request.getTags().stream()
                .map(t -> new Tag(t, true, Taggable.entity, ""))
                .collect(Collectors.toList());
        schema.setTags(tags);
        return schema;
    }



    public AttributeDefinition convertFieldCreateRequest(String entityId, FieldRequest request) {
        AttributeDefinition attr = new AttributeDefinition();
        String attributeId = ObjectId.get().toString();

        if (StringUtils.isNotEmpty(entityId)){
            attr.setEntityId(entityId);
        }
        attr.setApiName(request.getApiName());
        attr.setDisplayName(request.getDisplayName());
        attr.setDataStoreName(request.getDatastoreName());
        attr.setDataType(DatatypeFactory.getDatatype(request.getDataType()));
        if (request.getDescription() != null)
            attr.setDescription(request.getDescription());
        if (request.getLength() != null)
            attr.setLength(request.getLength());
        if (request.getMultiValueField() != null)
            attr.setMultiValueField(request.getMultiValueField());
        if (request.getRequired() != null)
            attr.setNillable(!request.getRequired());
        if (request.getUnique() != null)
            attr.setUnique(request.getUnique());
        if (request.getPicklistValues() != null)
            attr.setPicklistValues(request.getPicklistValues());
        if (request.getTags() != null) {
            var tags = request.getTags().stream()
                    .map(t -> new Tag(t, true, Taggable.attribute, attributeId))
                    .collect(Collectors.toList());
            attr.setTags(tags);
        }

        return attr;
    }

    public boolean validateSynapseType(String connectorTypeId) {
        ConnectorMetadata connectorMetadata = connectorMetadataService.findById(connectorTypeId).orElseThrow(() ->
                new NotFoundException(i18n("connector_metadata_not_found", connectorTypeId)));

        if(KaribuConstants.SYNAPSES_THAT_SUPPORT_FIELD_CREATION.contains(connectorMetadata.getName()))
            return true;

        return false;
    }

    public List<String> validateFieldUpdateRequest(String entityId, List<FieldRequest> requests) {
        List<String> errors = new ArrayList<>();

        // get entity
        EntityDefinition entity = schemaService.getEntity(entityId);

        // check syncari synapse
        ConnectorMetadata connectorMetadata = connectorMetadataService.findById(entity.getConnectorTypeId()).orElseThrow(() ->
                new NotFoundException(i18n("connector_metadata_not_found", entity.getConnectorTypeId())));

        if(!KaribuConstants.SYNAPSES_THAT_SUPPORT_FIELD_UPDATE.contains(connectorMetadata.getName()))
            throw new BadRequestException(i18n("field_update_unsupported_synapse"));

        // check if entity is already published
        if(entity.isApproved())
            errors.add(i18n("entity_not_draft", entityId));

        for(FieldRequest request : requests) {
            try {
                // validate request
                if(null == request.getFieldId())
                    errors.add(i18n("field_id_null"));

                // get attribute
                AttributeDefinition attribute = new AttributeDefinition();
                attribute = schemaService.getAttribute(request.getFieldId());

                // verify entity ids match
                if(!entityId.equals(attribute.getEntityId()))
                    errors.add(i18n("entity_field_mismatch", request.getFieldId(), entityId));

                // check if field is already published
                if(attribute.isApproved())
                    errors.add(i18n("field_not_draft", request.getFieldId()));

                // validate request
                if(null != request.getApiName())
                    errors.add(i18n("field_api_name_not_null"));

                // cannot update an id field
                if(attribute.isIdField())
                    errors.add(i18n("field_update_not_on_id", request.getFieldId()));

                // cannot update an watermark field
                if(attribute.isWatermarkField())
                    errors.add(i18n("field_update_not_on_watermark", request.getFieldId()));

                // cannot update an reference fields on a non reference datatype
                if(request.getDataType() == null && !attribute.getDataType().getName().equals("reference")
                        && (request.getReferenceTo() != null || request.getReferenceTargetField() != null))
                    errors.add(i18n("field_update_not_on_reference", request.getFieldId()));

                // cannot update an reference fields on a non reference datatype
                if(request.getDataType() != null && !request.getDataType().equals("reference")
                        && (request.getReferenceTo() != null || request.getReferenceTargetField() != null))
                    errors.add(i18n("field_update_not_on_reference", request.getFieldId()));

                // verify datatype
                if(request.getDataType() != null && DatatypeFactory.getDatatype(request.getDataType()).getName().equals("string")
                        && !request.getDataType().toLowerCase().equals("string"))
                    errors.add(i18n("field_update_bad_datatype", request.getDataType(), request.getFieldId()));


            } catch(Exception e) {
                if (StringUtils.contains(e.getMessage(), "not found"))
                    errors.add(i18n("field_not_found", request.getFieldId(), request.getFieldId()));
            }

        }

        return errors;
    }

    public AttributeDefinition getUpdateAttribute(AttributeDefinition attribute, FieldRequest request){
        if (request.getDisplayName() != null)
            attribute.setDisplayName(request.getDisplayName());
        if (request.getDatastoreName() != null)
            attribute.setDataStoreName(request.getDatastoreName());
        if (request.getDescription() != null)
            attribute.setDescription(request.getDescription());
        if (request.getDataType() != null)
            attribute.setDataType(DatatypeFactory.getDatatype(request.getDataType()));
        if (request.getReferenceTo() != null)
            attribute.setReferenceTo(request.getReferenceTo());
        if (request.getReferenceTargetField() != null)
            attribute.setReferenceTargetField(request.getReferenceTargetField());
        if (request.getLength() != null)
            attribute.setLength(request.getLength());
        if (request.getMultiValueField() != null)
            attribute.setMultiValueField(request.getMultiValueField());
        if (request.getRequired() != null)
            attribute.setNillable(!request.getRequired());
        if (request.getUnique() != null)
            attribute.setUnique(request.getUnique());
        if(request.getPicklistValues() != null)
            attribute.setPicklistValues(request.getPicklistValues());

        if(request.getTags() != null) {
            var tags = request.getTags().stream()
                    .map(t -> new Tag(t, true, Taggable.attribute, attribute.getId()))
                    .collect(Collectors.toList());
            attribute.setTags(tags);
        }

        return attribute;
    }



}
