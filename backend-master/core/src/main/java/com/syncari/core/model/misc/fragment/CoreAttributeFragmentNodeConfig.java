package com.syncari.core.model.misc.fragment;

import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.CoreAttributeNodeConfig;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@Accessors(chain=true)
public class CoreAttributeFragmentNodeConfig extends CoreAttributeNodeConfig {

    @Override
    public Map<String, Object> getConfigMap() {
        return Map.of("dataAuthority", getDataAuthority().getConfigMap());
    }

    public CoreAttributeFragmentNodeConfig setAttributeDefinition(AttributeDefinition attributeDefinition){
        super.setAttributeDefinition(attributeDefinition);
        return this;
    }
}
