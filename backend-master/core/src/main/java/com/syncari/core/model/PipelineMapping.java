package com.syncari.core.model;

import com.syncari.core.model.util.Scope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Accessors(chain = true)
public class PipelineMapping extends UUIDAuditModel {
    public PipelineMapping(){}
    //AttributeDefinitionId, EntityDefinitionId or ConnectorId, depending on scope
    //A Polymorphic FK of sorts
    private String targetId;
    private Scope scope;

    private String pipelineId;

    //List of all attributes connected to this attribute. May be empty
    // for example, in cases where the attribute is mapped to an aggregate function on another entity
    private List<String> connectedAttributeIds = new ArrayList<>();

    public static PipelineMapping forAttribute(String attributeId, String pipelineId){
        return new PipelineMapping()
                .setScope(Scope.ATTRIBUTE)
                .setTargetId(attributeId)
                .setPipelineId(pipelineId);
    }

    public static PipelineMapping forEntity(String entityDefinitionId, String pipelineId){
        return new PipelineMapping()
                .setScope(Scope.ENTITY)
                .setTargetId(entityDefinitionId)
                .setPipelineId(pipelineId);
    }

}


