package com.syncari.core.model.misc.fragment;

import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.EntityDefinition;

import java.util.HashMap;
import java.util.Map;

public class CoreEntityFragmentNodeConfig extends CoreEntityNodeConfig {

    @Override
    public Map<String, Object> getConfigMap() {
        Map<String, Object> config = new HashMap<>();
        config.putAll(getDataAuthority().getConfigMap());
        config.putAll(getDedupeConfig().getConfigMap());
        if(getAdvancedDedupeConfig() !=null){
            config.putAll(getAdvancedDedupeConfig().getConfigMap());
        }
        return config;
    }

    public CoreEntityFragmentNodeConfig setEntityDefinition(EntityDefinition entityDefinition){
        super.setEntityDefinition(entityDefinition);
        return this;
    }
}
