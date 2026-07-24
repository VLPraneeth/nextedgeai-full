package com.syncari.connector.database;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.EntitySchema;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class CompositeKeyHelper {

    @Deprecated
    // This code seems to have a bug (moved from DatabaseService for refactoring purpose). It should also include the name of main id field
    public String composeKeys(EntityData entityData, EntitySchema entitySchema) {
        var compositeKeyFieldList = entitySchema.getCompositeKeyFields();
        return compositeKeyFieldList.stream().map(c -> entityData.getValueOptional(c.getApiName()))
                .filter(Optional::isPresent)
                .map(Optional::get).map(Object::toString)
                .collect(Collectors.joining(EntitySchema.COMPOSITE_KEY_DELIMETER));
    }

    public String composeIdKeys(EntityData entityData, EntitySchema entitySchema) {
        var compositeKeyFieldList = entitySchema.getCompositeKeyAttributes();
        return compositeKeyFieldList.stream().map(c -> entityData.getValueOptional(c.getApiName()))
                .filter(Optional::isPresent)
                .map(Optional::get).map(Object::toString)
                .collect(Collectors.joining(EntitySchema.COMPOSITE_KEY_DELIMETER));
    }

    public Optional<String> getCompositeValuePredicate(EntitySchema entitySchema, String id) {

        if (entitySchema.hasIdField() && StringUtils.isBlank(entitySchema.getIdField().getCompositeKey())) {
            return Optional.of(String.format("'%s'", id));
        } else {
            String splitArray[] = id.split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            var compositeKeyFieldList = entitySchema.getCompositeKeyFields();
            if ((ArrayUtils.isNotEmpty(splitArray)) && (org.apache.commons.collections.CollectionUtils.isNotEmpty(compositeKeyFieldList)) && (splitArray.length == compositeKeyFieldList.size())) {
                return Optional.of(String.format("(%s)", Arrays.stream(splitArray).map(v -> String.format("'%s'", v)).collect(Collectors.joining(","))));
            }
            return Optional.empty();
        }
    }

    public List<Object> getCompositeValueTyped(EntitySchema entitySchema, String id) {

        if (entitySchema.hasIdField() && !StringUtils.isBlank(entitySchema.getIdField().getCompositeKey())) {
            String splitArray[] = id.split(Pattern.quote(EntitySchema.COMPOSITE_KEY_DELIMETER));
            var compositeKeyFieldList = entitySchema.getCompositeKeyFields();
            if ((ArrayUtils.isNotEmpty(splitArray)) && (org.apache.commons.collections.CollectionUtils.isNotEmpty(compositeKeyFieldList)) && (splitArray.length == compositeKeyFieldList.size())) {
                return IntStream.range(0, compositeKeyFieldList.size()).mapToObj(i -> convertIdValue(compositeKeyFieldList.get(i).getDataType(), splitArray[i])).collect(Collectors.toList());
            }
        }
        return List.of(id);
    }

    private Object convertIdValue(String dataType, String value) {

        switch (dataType) {
            case "int":
            case "integer" :
                return Long.parseLong(value);
            default:
                return value;
        }
    }
}
