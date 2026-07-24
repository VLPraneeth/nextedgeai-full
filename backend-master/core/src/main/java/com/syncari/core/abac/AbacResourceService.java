package com.syncari.core.abac;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.abac.AbacAttributeValue;
import com.syncari.core.model.abac.ResourceType;
import com.syncari.core.repositories.customer.AbacAttributeRepo;
import com.syncari.core.repositories.customer.AbacAttributeValueRepo;
import com.syncari.core.token.TokenHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbacResourceService {
  @Autowired
  private TokenHelper tokenHelper;
  @Autowired
  protected AbacAttributeRepo attribRepo;
  @Autowired
  protected AbacAttributeValueRepo valueRepo;
  @Autowired
  private ObjectMapper objectMapper;
  
  public abstract Object checkSingle(AbacContext context, Object data);
  public abstract List<Object> checkList(AbacContext context, Iterable<Object> data);
  
  public Object convertData(String dataType, Object value) {
    if (value == null) {
      return value;
    }

    if (value instanceof Collection) {
      return ((Collection<?>) value).stream().map(v -> convertData(dataType, v)).collect(Collectors.toList());
    }

    if (value instanceof Date) {
      return ((Date) value).getTime();
    }

    if (value instanceof LocalDate) {
      return ((LocalDate) value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    if (value instanceof LocalDateTime) {
      return ((LocalDateTime) value).atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    if (value instanceof ZonedDateTime) {
      return ((ZonedDateTime) value).toInstant().toEpochMilli();
    }

    if (value instanceof Instant) {
      return ((Instant) value).toEpochMilli();
    }
    switch (dataType) {
      case BooleanType.NAME:
        return BooleanType.VALUE.convert(value);
      case DoubleType.NAME:
      case IntegerType.NAME:
        return IntegerType.VALUE.convert(value);
      case DateType.NAME:
        var converted = DateType.VALUE.convert(value);
        return converted == null ? null : converted.getTime();
      case DatetimeType.NAME:
        var convertedDt = DatetimeType.VALUE.convert(value);
        return convertedDt == null ? null : convertedDt.toInstant().toEpochMilli();
      case "object":
        return ObjectType.VALUE.convert(value);
      default:
        return StringType.VALUE.convert(value);
    }
  }
  
  private Object resolveToken(Object context, Object value) {
    if (value instanceof String) {
      String valStr = value.toString();
      if (tokenHelper.hasTokens(valStr)) {
        if(context instanceof Map) {
          return tokenHelper.resolveTokens((Map<String, Object>) context, valStr).y;
        } else {
          return tokenHelper.resolveTokens(Map.of("resource", Map.of("values", convertObjectToMap(context))), valStr).y;
        }
      }
    }
    return value;
  }
  
  protected void mapAttribute(ResourceType type, Map<String, Object> ret, AbacAttributeValue val,
      Object context) {
    var attrOpt = attribRepo.findById(val.getAttributeId());
    if (attrOpt.isPresent()) {
      String key = attrOpt.get().getApiName();
      if (type == ResourceType.USER) {
        key = String.format("%s_%s", SyncariContext.getSyncariId(), key);
      }
      if (type == attrOpt.get().getResourceType()) {
        if (attrOpt.get().isMultiValued() && !List.class.isAssignableFrom(List.class)) {
          ret.put(key, List.of(convertData(attrOpt.get().getDataType(), resolveToken(context, val.getValue()))));
        } else {
          ret.put(key, convertData(attrOpt.get().getDataType(), resolveToken(context, val.getValue())));
        }
      }
    }
  }

  public Map<String, Object> getUserAttributeValuesAsMap() {
    Map<String, Object> ret = new HashMap<String, Object>();
    valueRepo.findByResourceId(SyncariContext.getUser().getId()).forEach(val -> {
      mapAttribute(ResourceType.USER, ret, val, SyncariContext.getUser());
    });
    return ret;
  }
  
  public Map<String, Object> convertObjectToMap(Object obj) {
    try {
      return objectMapper.convertValue(obj, Map.class);
    } catch (IllegalArgumentException e) {
      log.warn("Failed to convert object to map ", e);
      return Map.of();
    }
}
}
