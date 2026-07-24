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
public class FunctionResponse extends BaseKaribuResponse {
    private String apiName;
    private String displayName;
    private String description;
    private String helpSummary;

    private Datatype outputType;
    private Scope scope = Scope.ATTRIBUTE;
    private Type type = Type.STANDARD;
    private List<FunctionConfiguration> configuration = new ArrayList<>();

    @Override
    public <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object) {
        FunctionDefinition function = (FunctionDefinition) object;
        FunctionResponse response = new FunctionResponse();

        response.setId(function.getId());
        response.setApiName(function.getName());
        response.setDisplayName(function.getDisplayName());
        response.setDescription(function.getDescription());
        response.setHelpSummary(function.getHelpSummary());
        response.setScope(function.getScope());
        response.setOutputType(function.getOutputType());
        response.setConfiguration(function.getConfiguration());
        response.setType(function.getType());
        response.setCreatedBy(function.getCreatedBy());
        response.setCreatedAt(function.getCreatedAt());
        response.setUpdatedBy(function.getUpdatedBy());
        response.setUpdatedAt(function.getUpdatedAt());
        return response;

    }
    
}
