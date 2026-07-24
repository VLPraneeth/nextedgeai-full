package com.syncari.core.mapper;

public enum MapperType {
    BASIC_SEARCH("basicSearch"),
    SYNC_AI("syncAI");

    private final String value;

    MapperType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MapperType fromValue(String mapperType) {
        if (mapperType == null) {
            return BASIC_SEARCH; // Default to basic mapper
        }

        for (MapperType type : MapperType.values()) {
            if (type.value.equalsIgnoreCase(mapperType)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown mapper type: " + mapperType +
                ". Valid values are: basicSearch, syncAI");
    }
}