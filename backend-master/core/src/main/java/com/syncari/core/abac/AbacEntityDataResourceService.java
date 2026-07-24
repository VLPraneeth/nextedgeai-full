package com.syncari.core.abac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.lang.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.syncari.connector.EntityData;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.customer.AbacAttributeValueRepo;
import com.syncari.core.repositories.customer.EntityDefinitionCache;
import com.syncari.core.service.ConnectorService;

@Component
public class AbacEntityDataResourceService extends AbacResourceService {

  @Autowired
  private AbacService abac;
  @Autowired
  private AbacAttributeValueRepo valueRepo;
  @Autowired
  private EntityDefinitionCache entityRepo;
  @Autowired
  private ConnectorService connectorService;

  @Override
  public Object checkSingle(AbacContext context, Object data) {
    EntityData ed = (EntityData) data;
    context.setResourceType(
        context.getResourceType() != null ? context.getResourceType() : ResourceType.ENTITY_DATA);
    context.setAction(context.getAction() == null ? Permission.READ : context.getAction());
    context.setResource(context.getResource() != null ? context.getResource()
        : ed.getName());
    context.setUserAttributes(getUserAttributeValuesAsMap());
    Map<String, Map<String, Object>> resourceAttributes = new HashMap<>();
    context.setResourceAttributes(resourceAttributes);
    var entityDef = entityRepo.findByConnectorIdAndApiName(connectorService.getSyncariConnector().getId(), ed.getName());
    resourceAttributes.put(ed.getId(), getAttributeValuesAsMap(ResourceType.ENTITY_DATA, entityDef.get(), ed));
    return filter(data, abac.check(context));
  }

  @Override
  public List<Object> checkList(AbacContext context, Iterable<Object> data) {
    context.setResourceType(
        context.getResourceType() != null ? context.getResourceType() : ResourceType.ENTITY_DATA);
    context.setAction(context.getAction() == null ? Permission.READ : context.getAction());
    context.setUserAttributes(getUserAttributeValuesAsMap());
    Map<String, Map<String, Object>> resourceAttributes = new HashMap<>();
    context.setResourceAttributes(resourceAttributes);
    Map<String, EntityData> eds = new LinkedHashMap<>();
    List<String> syncariEds = new ArrayList<>();
    data.forEach(e -> {
      EntityData def = (EntityData) e;
      context.setResource(context.getResource() != null ? context.getResource()
          : def.getName());
      var entityDef = entityRepo.findByConnectorIdAndApiName(connectorService.getSyncariConnector().getId(), def.getName());
      resourceAttributes.put(def.getId(), getAttributeValuesAsMap(ResourceType.ENTITY_DATA, entityDef.get(), def));
      eds.put(def.getId(), def);
      syncariEds.add(def.getId());
    });
    if (syncariEds.isEmpty()) {
      return StreamSupport.stream(data.spliterator(), false).collect(Collectors.toList());
    }
    List<EntityData> filteredDefs =
        (List<EntityData>) filter(data, abac.check(context));
    syncariEds.removeAll(filteredDefs.stream().map(e -> e.getId()).collect(Collectors.toList()));
    syncariEds.forEach(eid -> {
      eds.remove(eid);
    });
    return new ArrayList<Object>(eds.values());
  }

  public Object filter(Object data, Map<String, Boolean> abacResponse) {
    if (abacResponse.isEmpty()) {
      return data;
    }
    List<Object> incoming = new ArrayList<Object>();
    if (data instanceof Iterable<?>) {
      incoming.addAll(StreamSupport.stream(((Iterable<Object>) data).spliterator(), false)
          .collect(Collectors.toList()));
    } else if (data instanceof Optional && ((Optional) data).isPresent()) {
      incoming.add(((Optional) data).get());
    } else {
      incoming.add(data);
    }
    List<Object> filtered = new ArrayList<Object>();
    incoming.forEach(d -> {
      EntityData def = (EntityData) d;
      if (BooleanUtils.isTrue(abacResponse.get(def.getId()))) {
        filtered.add(def);
      }
    });
    if (data instanceof List<?>) {
      return filtered;
    } else if (data instanceof Optional) {
      if (filtered.isEmpty()) {
        return Optional.empty();
      } else {
        return Optional.of(filtered.get(0));
      }
    } else {
      if (filtered.isEmpty()) {
        return null;
      } else {
        return filtered.get(0);
      }
    }
  }

  public Map<String, Object> getAttributeValuesAsMap(ResourceType type, EntityDefinition def, EntityData data) {
    Map<String, Object> ret = new HashMap<String, Object>();
    valueRepo.findByResourceId(def.getId()).forEach(val -> {
      mapAttribute(type, ret, val, data.getValues());
    });
    return ret;
  }

}
