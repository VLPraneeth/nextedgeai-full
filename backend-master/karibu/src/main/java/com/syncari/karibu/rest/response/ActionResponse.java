package com.syncari.karibu.rest.response;

import java.util.ArrayList;
import java.util.List;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.UUIDAuditModel;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper=true)
public class ActionResponse extends BaseKaribuResponse {
    private String apiName;
    private String displayName;
    private String description;
    private String helpSummary;
    private String llmHint;

    private Datatype outputType;
    private Scope scope = Scope.ATTRIBUTE;
    private Type type = Type.STANDARD;
    private List<FunctionConfiguration> configuration = new ArrayList<>();

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        FunctionDefinition action = (FunctionDefinition) object;
        ActionResponse response = new ActionResponse();

        response.setId(action.getId());
        response.setApiName(action.getName());
        response.setDisplayName(action.getDisplayName());
        response.setDescription(action.getDescription());
        response.setHelpSummary(action.getHelpSummary());
        response.setScope(action.getScope());
        response.setOutputType(action.getOutputType());
        response.setConfiguration(action.getConfiguration());
        response.setType(action.getType());
        response.setDescription(action.getDescription());
        response.setCreatedBy(action.getCreatedBy());
        response.setCreatedAt(action.getCreatedAt());
        response.setUpdatedBy(action.getUpdatedBy());
        response.setUpdatedAt(action.getUpdatedAt());
        return response;

    }
    
}
