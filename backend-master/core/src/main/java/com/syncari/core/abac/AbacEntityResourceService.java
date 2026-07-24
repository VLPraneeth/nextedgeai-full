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
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.abac.AbacAttribute;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.Permission;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacAttributeValueRepo;
import com.syncari.core.service.ConnectorService;

@Component
public class AbacEntityResourceService extends AbacResourceService {

  @Autowired
  private ConnectorService connectorService;
  @Autowired
  private AbacService abac;
  @Autowired
  private AbacAttributeValueRepo valueRepo;
  @Autowired
  private AbacAttributeRepo attributeRepo;

  @Override
  public Object checkSingle(AbacContext context, Object data) {
    String connectorId = connectorService.getSyncariConnector().getId();
    EntityDefinition ed = (EntityDefinition) data;
    if (connectorId.equals(ed.getConnectorId())) {
      context.setResourceType(
          context.getResourceType() != null ? context.getResourceType() : ResourceType.ENTITY);
      context.setAction(context.getAction() == null ? Permission.READ : context.getAction());
      context.setResource(context.getResource() != null ? context.getResource()
          : ResourceType.ENTITY.name().toLowerCase());
      context.setUserAttributes(getUserAttributeValuesAsMap());
      Map<String, Map<String, Object>> resourceAttributes = new HashMap<>();
      context.setResourceAttributes(resourceAttributes);
      resourceAttributes.put(ed.getId(),
          getAttributeValuesAsMap(ResourceType.ENTITY, ed));
      return filter(data, abac.check(context));
    }
    return data;
  }

  @Override
  public List<Object> checkList(AbacContext context, Iterable<Object> data) {
    context.setResourceType(
        context.getResourceType() != null ? context.getResourceType() : ResourceType.ENTITY);
    context.setAction(context.getAction() == null ? Permission.READ : context.getAction());
    context.setResource(context.getResource() != null ? context.getResource()
        : ResourceType.ENTITY.name().toLowerCase());
    context.setUserAttributes(getUserAttributeValuesAsMap());
    Map<String, Map<String, Object>> resourceAttributes = new HashMap<>();
    context.setResourceAttributes(resourceAttributes);
    String connectorId = connectorService.getSyncariConnector().getId();
    Map<String, EntityDefinition> eds = new LinkedHashMap<>();
    List<String> syncariEds = new ArrayList<>();
    data.forEach(e -> {
      EntityDefinition def = (EntityDefinition) e;
      resourceAttributes.put(def.getId(),
          getAttributeValuesAsMap(ResourceType.ENTITY, def));
      eds.put(def.getId(), def);
      if (connectorId.equals(def.getConnectorId())) {
        syncariEds.add(def.getId());
      }
    });
    if (syncariEds.isEmpty()) {
      return StreamSupport.stream(data.spliterator(), false)
          .collect(Collectors.toList());
    }
    List<EntityDefinition> filteredDefs = (List<EntityDefinition>) filter(data, abac.check(context));
    syncariEds.removeAll(filteredDefs.stream().map(e -> e.getId()).collect(Collectors.toList()));
    syncariEds.forEach(eid -> {
      eds.remove(eid);
    });
    return new ArrayList<Object>(eds.values());
  }

  public Object filter(Object data, Map<String, Boolean> abacResponse) {
    if(abacResponse.isEmpty()) {
      return data;
    }
    List<Object> incoming = new ArrayList<Object>();
    if (data instanceof Iterable<?>) {
      incoming.addAll(StreamSupport.stream(((Iterable<Object>)data).spliterator(), false)
          .collect(Collectors.toList()));
    } else if (data instanceof Optional && ((Optional) data).isPresent()) {
      incoming.add(((Optional) data).get());
    } else {
      incoming.add(data);
    }
    List<Object> filtered = new ArrayList<Object>();
    incoming.forEach(d -> {
      EntityDefinition def = (EntityDefinition) d;
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

  public Map<String, Object> getAttributeValuesAsMap(ResourceType type, EntityDefinition def) {
    List<AbacAttribute> attribs = attributeRepo.findByResourceType(type);
    List<AbacAttributeValue> values = valueRepo.findByResourceIdIn(
        def.getParentId() != null ? List.of("__all__", def.getId(), def.getParentId())
            : List.of("__all__", def.getId()));
    Map<String, Object> ret = new HashMap<String, Object>();
    values.forEach(val -> {
      mapAttribute(type, ret, val, def);
    });
    attribs.forEach(attr -> {
      if (!ret.containsKey(attr.getApiName())) {
        if (List.of("text", "string", "enumeration").contains(attr.getDataType())) {
          ret.put(attr.getApiName(), "");
        }
      }
    });
    return ret;
  }

}
