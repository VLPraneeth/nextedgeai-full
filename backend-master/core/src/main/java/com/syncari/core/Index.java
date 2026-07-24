package com.syncari.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.conversions.Bson;

@Data
@Accessors(chain = true)
public class Index {
    int ascending=1;
    List<String> fields = new ArrayList<>();
    boolean isUnique=true;
    String name;
    boolean caseSensitive = true;
    Long expireAfterSeconds;
    Bson partialFilterExpression;
    // This can be used if there needs to be a different order for any field in a compound index. Should contain same fieldNames as key as defined for fieldName
    Map<String, Integer> fieldsOrderMap = new HashMap<>();

    public Index(String... fieldName) {
        fields.addAll(List.of(fieldName));
    }

    public Index(boolean isUnique, int ascending, String... fieldName) {
        this.isUnique = isUnique;
        this.ascending = ascending;
        fields.addAll(List.of(fieldName));
    }

    public Index(String name, boolean isUnique, int ascending, String... fieldName) {
        this(isUnique, ascending, fieldName);
        this.name = name;
    }

    public Index(boolean isUnique,String... fieldName) {
        this(fieldName);
        this.isUnique = isUnique;
    }

    public Index(boolean isUnique,Map<String, Integer> fieldsOrderMap,String... fieldName) {
        this(fieldName);
        this.isUnique = isUnique;
        this.fieldsOrderMap = fieldsOrderMap;
    }

    public Index(String name, boolean isUnique,String... fieldName) {
        this(isUnique, fieldName);
        this.name = name;
    }

    public Index(String name, boolean isUnique, boolean caseSensitive, String... fieldName) {
        this(name, isUnique, fieldName);
        this.caseSensitive = caseSensitive;
    }

    public Index(String name, boolean isUnique, boolean caseSensitive, Long expireAfterSeconds, String... fieldName) {
        this(name, isUnique, caseSensitive, fieldName);
        this.expireAfterSeconds = expireAfterSeconds;
    }

    public Index(String name, boolean isUnique, boolean caseSensitive, Long expireAfterSeconds, Bson partialFilterExpression, String... fieldName) {
        this(name, isUnique, caseSensitive, expireAfterSeconds, fieldName);
        this.partialFilterExpression = partialFilterExpression;
    }
}
