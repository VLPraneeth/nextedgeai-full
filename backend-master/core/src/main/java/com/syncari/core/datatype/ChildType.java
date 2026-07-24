package com.syncari.core.datatype;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.syncari.connector.EntityData;
import com.syncari.connector.ExternalId;
import com.syncari.core.config.ZonedDateTimeReadConverter;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@EqualsAndHashCode
@Slf4j
public class ChildType extends AbstractDataType<EntityData> {
    public static final String NAME = "child";
    public static final ChildType VALUE = new ChildType();
    public static final Map<Class<?>, Function<Object, EntityData>> CONVERTERS = Map.of(
            EntityData.class, value -> (EntityData) value,
            Map.class, value -> fromMap(Map.class.cast(value))
        );

    private static EntityData fromMap(Map<String, Object> value) {
        EntityData entityData = new EntityData("childObject");
        value.forEach((apiName,v)->{

            // if this value is of type date, convert to ZoneDateTime
            if (v != null && v instanceof Date) {
                v = new ZonedDateTimeReadConverter().convert((Date) v);
            }
            entityData.addValue(apiName,v);
        });
        entityData.setSyncariEntityId(Objects.toString(value.get("syncariId"),null));
        entityData.setName(Objects.toString(value.get("syncariEntityName"),"childObject"));
        return entityData;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Class<EntityData> getJavaType() {
        return EntityData.class;
    }

    @Override
    public boolean canConvert(Datatype other) {
       return true;
    }

    @Override
    protected Map<Class<?>, Function<Object, EntityData>> getConverters() {
        return CONVERTERS;
    }

}
