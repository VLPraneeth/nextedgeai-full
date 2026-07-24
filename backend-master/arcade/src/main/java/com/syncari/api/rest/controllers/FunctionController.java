package com.syncari.api.rest.controllers;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.NodeDef;
import com.syncari.core.model.util.Scope;
import static com.syncari.core.security.Permissions.READ_STUDIO;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.KeyValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/functions")
public class FunctionController {
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    MappingGraphService graphService;

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/entity/{syncariEntityId}")
    public List<NodeDef> getFunctionsWithEntityContext(@PathVariable String syncariEntityId) {
		List<NodeDef> results = transformer.toFunctionDef(schemaService.getFunctions(Scope.ENTITY));
		results.addAll(transformer.toFunctionDef(schemaService.getFunctions(Scope.ENTITY_AND_ATTRIBUTE)));
		return results;
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/field/{graphId}")
    public List<NodeDef> getFunctionsWithFieldContext(@PathVariable String graphId) {
        return transformer.toFunctionDef(schemaService.getFunctions(Scope.ATTRIBUTE));
    }

}
@Data
@Accessors(chain = true)
class ConfigurationEntry{
	private Map<String, Object> mapping=new HashMap<>();
	private String datatype;
	private boolean implicit;
	private Object defaultValue;
	private String name;
	private String label;
	private String fieldSet;
	private String type;
	private KeyValue dependsOn;
	private List<KeyValue> values=new ArrayList<>();
	private Object value;

	public ConfigurationEntry addMapping(String key, Object value){
		mapping.put(key, value);
		return this;
	}
	public ConfigurationEntry addGraphKey( Object value){
		mapping.put("graphKey", value);
		return this;
	}

	public ConfigurationEntry setDependency(String dependantField, String dependantType){
		dependsOn = new KeyValue("dependantField",dependantField).set("dependantType",dependantType);
		return this;
	}

	public KeyValue toKeyValue(){
		KeyValue kv = new KeyValue("name", name).set("datatype", datatype).set("label", label).set("defaultValue",defaultValue);
		if(type!=null) kv.set("type",type);
		if(!mapping.isEmpty()) kv.set("mapping",mapping);
		if(fieldSet!=null) kv.set("fieldSet",fieldSet);
		if(value!=null) kv.set("value",fieldSet);
		if(!values.isEmpty()) kv.set("values",values);
		if(dependsOn!=null) kv.set("dependsOn",dependsOn);
		return kv;
	}
}
