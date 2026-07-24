package com.syncari.api.rest.controllers.data;

import com.syncari.core.model.EngineType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.model.util.Type;
import com.syncari.utils.KeyValue;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
@AllArgsConstructor
public class NodeDef {
	private String id;
    private String name;
    private String displayName;
    private String description;
    private String helpSummary;
    private String helpPath;
    private String iconPath;
    private String outputType;
    private List<Param> positionalParams;
    private EngineType engineType;
    private Scope scope ;
    private Type type ;
    private List<KeyValue> configuration=new ArrayList<>();
    private boolean dynamicConfig =false;
    private boolean hidden =false;
    //default renderer is form
    private Renderer renderer;

    public NodeDef replaceConfig(KeyValue newConfig){
        Optional<KeyValue> existing = configuration.stream().filter(c -> c.get("name").equals(newConfig.get("name"))).findFirst();
        existing.ifPresent( e -> {
            configuration.remove(e);
        });
        configuration.add(newConfig);
        return this;
    }
    
    public NodeDef removeConfig(String configName){
        Optional<KeyValue> existing = configuration.stream().filter(c -> c.get("name").equals(configName)).findFirst();
        existing.ifPresent( e -> {
            configuration.remove(e);
        });
        return this;
    }

    public NodeDef replaceConfigValue(String configName, String configKey, Object configValue){
        Optional<KeyValue> existing = configuration.stream().filter(c -> c.get("name").equals(configName)).findFirst();
        existing.ifPresent(e-> e.set(configKey,configValue));
        return this;
    }

    public Optional<KeyValue> findConfiguration(String queryKey,Object queryValue){
        return configuration.stream().filter(kv -> Objects.equals(queryValue, kv.get(queryKey))).findFirst();
    }
    public NodeDef addConfiguration(KeyValue keyValue){
        configuration.add(keyValue);
        return this;
    }

    public Optional<KeyValue> findConfigurationByName(String name){
        return configuration.stream().filter(kv -> Objects.equals(name, kv.get("name"))).findFirst();
    }
}

