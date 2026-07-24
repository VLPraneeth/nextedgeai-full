package com.syncari.connector.mongodb;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MongoDBTypeMapper {

    // Internal Syncari fields that should not be read from source MongoDB collections
    private static final Set<String> INTERNAL_FIELDS = Set.of(
        "syncariScore",
        "syncariTimestamp",
        "syncariCreatedAt",
        "syncariEntityId",
        "syncariParentEntityId",
        "reparented",
        "originatingConnectorId",
        "dedupeHash",
        "outlierTimestamp"
    );

    /**
     * Convert MongoDB Document to EntityData
     */
    public EntityData documentToEntityData(Document doc, EntitySchema schema, String entityName) {
        EntityData entityData = new EntityData();
        entityData.setName(entityName);

        String idField = schema.getIdField() != null ? schema.getIdField().getApiName() : "_id";
        String wmField = schema.getWatermarkField() != null ? schema.getWatermarkField().getApiName() : null;
        List<String> compositeKeyFields = schema.getCompositeKeyFieldNames();

        // Process each field in the document
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            // Skip internal Syncari fields that are on EntityData itself, not in values
            if (INTERNAL_FIELDS.contains(fieldName)) {
                // Skip - these are internal Syncari fields, not source fields
                log.debug("Skipping internal Syncari field: {}", fieldName);
                continue;
            }

            Optional<AttributeSchema> fieldSchema = schema.getField(fieldName);
            Object convertedValue = convertBsonToJava(value, fieldSchema);

            // Handle ID field
            if (fieldName.equals(idField)) {
                String idValue = convertIdValue(value);
                entityData.setId(idValue);
                entityData.addValue(fieldName, convertedValue);
            }
            // Handle composite key fields
            else if (compositeKeyFields.contains(fieldName)) {
                entityData.addCompositeKey(fieldName, convertedValue);
                entityData.addValue(fieldName, convertedValue);
            }
            // Handle watermark field
            else if (wmField != null && fieldName.equals(wmField)) {
                long lastModified = extractTimestamp(value);
                entityData.setLastModified(lastModified);
                entityData.addValue(fieldName, convertedValue);
            }
            // Regular field
            else {
                entityData.addValue(fieldName, convertedValue);
            }
        }

        // Build composite ID if needed
        if (!compositeKeyFields.isEmpty() && entityData.getCompositeKeyData() != null &&
            !entityData.getCompositeKeyData().isEmpty() && entityData.getId() == null) {
            String compositeId = compositeKeyFields.stream()
                    .map(field -> String.valueOf(entityData.getCompositeKeyData().get(field)))
                    .collect(Collectors.joining(EntitySchema.COMPOSITE_KEY_DELIMETER));
            entityData.setId(compositeId);
        }

        return entityData;
    }

    /**
     * Convert EntityData to MongoDB Document
     */
    public Document entityDataToDocument(EntityData entityData, EntitySchema schema) {
        Document doc = new Document();

        String idField = schema.getIdField() != null ? schema.getIdField().getApiName() : "_id";
        Map<String, Object> values = entityData.getValues();

        if (values == null || values.isEmpty()) {
            return doc;
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            Optional<AttributeSchema> fieldSchema = schema.getField(fieldName);
            Object convertedValue = convertJavaToBson(value, fieldSchema, fieldName.equals(idField));

            doc.append(fieldName, convertedValue);
        }

        return doc;
    }

    /**
     * Convert BSON type to Java type based on schema
     */
    private Object convertBsonToJava(Object value, Optional<AttributeSchema> fieldSchema) {
        if (value == null) {
            return null;
        }

        String dataType = fieldSchema.map(AttributeSchema::getDataType).orElse("string");

        try {
            switch (dataType.toLowerCase()) {
                case "id":
                    return convertIdValue(value);

                case "string":
                    if (value instanceof ObjectId) {
                        return ((ObjectId) value).toHexString();
                    }
                    return value.toString();

                case "integer":
                case "int":
                case "long":
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                    return Long.parseLong(value.toString());

                case "double":
                case "float":
                case "decimal":
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    if (value instanceof Decimal128) {
                        return ((Decimal128) value).bigDecimalValue().doubleValue();
                    }
                    return Double.parseDouble(value.toString());

                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.parseBoolean(value.toString());

                case "timestamp":
                case "datetime":
                case "date":
                    return convertToZonedDateTime(value);

                case "object":
                    if (value instanceof Document) {
                        return documentToMap((Document) value);
                    }
                    if (value instanceof Map) {
                        return value;
                    }
                    return value;

                default:
                    // Handle arrays/lists
                    if (value instanceof List) {
                        return convertList((List<?>) value);
                    }
                    if (value instanceof Document) {
                        return documentToMap((Document) value);
                    }
                    return value;
            }
        } catch (Exception e) {
            log.warn("Failed to convert BSON value of type {} for dataType {}: {}",
                    value.getClass().getSimpleName(), dataType, e.getMessage());
            return value;
        }
    }

    /**
     * Convert Java type to BSON type based on schema
     */
    private Object convertJavaToBson(Object value, Optional<AttributeSchema> fieldSchema, boolean isIdField) {
        if (value == null) {
            return null;
        }

        String dataType = fieldSchema.map(AttributeSchema::getDataType).orElse("string");

        try {
            // Special handling for _id field
            if (isIdField && "_id".equals(fieldSchema.map(AttributeSchema::getApiName).orElse(""))) {
                return convertToObjectId(value);
            }

            switch (dataType.toLowerCase()) {
                case "id":
                    return convertToObjectId(value);

                case "timestamp":
                case "datetime":
                case "date":
                    if (value instanceof ZonedDateTime) {
                        return Date.from(((ZonedDateTime) value).toInstant());
                    }
                    if (value instanceof Date) {
                        return value;
                    }
                    if (value instanceof Number) {
                        return new Date(((Number) value).longValue());
                    }
                    if (value instanceof String) {
                        try {
                            ZonedDateTime zdt = ZonedDateTime.parse((String) value);
                            return Date.from(zdt.toInstant());
                        } catch (Exception e) {
                            return value;
                        }
                    }
                    return value;

                case "object":
                    if (value instanceof Map) {
                        return mapToDocument((Map<String, Object>) value);
                    }
                    return value;

                case "integer":
                case "int":
                    if (value instanceof Number) {
                        return ((Number) value).intValue();
                    }
                    return Integer.parseInt(value.toString());

                case "long":
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                    return Long.parseLong(value.toString());

                case "double":
                case "float":
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                    return Double.parseDouble(value.toString());

                case "boolean":
                    if (value instanceof Boolean) {
                        return value;
                    }
                    return Boolean.parseBoolean(value.toString());

                default:
                    // Handle lists
                    if (value instanceof List) {
                        return convertListToBson((List<?>) value);
                    }
                    return value;
            }
        } catch (Exception e) {
            log.warn("Failed to convert Java value of type {} for dataType {}: {}",
                    value.getClass().getSimpleName(), dataType, e.getMessage());
            return value;
        }
    }

    /**
     * Convert value to ObjectId (for _id fields)
     */
    private ObjectId convertToObjectId(Object value) {
        if (value instanceof ObjectId) {
            return (ObjectId) value;
        }
        if (value instanceof String) {
            String str = (String) value;
            if (ObjectId.isValid(str)) {
                return new ObjectId(str);
            }
            throw new IllegalArgumentException("Invalid ObjectId format: " + str);
        }
        throw new IllegalArgumentException("Cannot convert value of type " +
                value.getClass().getSimpleName() + " to ObjectId: " + value);
    }

    /**
     * Convert ID value to String
     */
    private String convertIdValue(Object value) {
        if (value instanceof ObjectId) {
            return ((ObjectId) value).toHexString();
        }
        return value.toString();
    }

    /**
     * Extract timestamp from various types
     */
    private long extractTimestamp(Object value) {
        if (value instanceof Date) {
            return ((Date) value).getTime();
        }
        if (value instanceof ObjectId) {
            return ((ObjectId) value).getDate().getTime();
        }
        if (value instanceof Number) {
            long timestamp = ((Number) value).longValue();
            // If timestamp is in seconds, convert to milliseconds
            if (timestamp < 10000000000L) {
                return timestamp * 1000;
            }
            return timestamp;
        }
        if (value instanceof String) {
            try {
                return ZonedDateTime.parse((String) value).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.warn("Failed to parse timestamp from string: {}", value);
            }
        }
        return System.currentTimeMillis();
    }

    /**
     * Convert value to ZonedDateTime
     */
    private ZonedDateTime convertToZonedDateTime(Object value) {
        if (value instanceof Date) {
            return ZonedDateTime.ofInstant(((Date) value).toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof ObjectId) {
            return ZonedDateTime.ofInstant(((ObjectId) value).getDate().toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Number) {
            long timestamp = ((Number) value).longValue();
            // If timestamp is in seconds, convert to milliseconds
            if (timestamp < 10000000000L) {
                timestamp = timestamp * 1000;
            }
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
        }
        if (value instanceof String) {
            try {
                return ZonedDateTime.parse((String) value);
            } catch (Exception e) {
                log.warn("Failed to parse ZonedDateTime from string: {}", value);
            }
        }
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Convert MongoDB Document to Map recursively
     */
    private Map<String, Object> documentToMap(Document doc) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Document) {
                map.put(entry.getKey(), documentToMap((Document) value));
            } else if (value instanceof List) {
                map.put(entry.getKey(), convertList((List<?>) value));
            } else if (value instanceof ObjectId) {
                map.put(entry.getKey(), ((ObjectId) value).toHexString());
            } else {
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    /**
     * Convert Map to MongoDB Document recursively
     */
    private Document mapToDocument(Map<String, Object> map) {
        Document doc = new Document();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                doc.append(entry.getKey(), mapToDocument((Map<String, Object>) value));
            } else if (value instanceof List) {
                doc.append(entry.getKey(), convertListToBson((List<?>) value));
            } else {
                doc.append(entry.getKey(), value);
            }
        }
        return doc;
    }

    /**
     * Convert List recursively (BSON to Java)
     */
    private List<Object> convertList(List<?> list) {
        return list.stream().map(item -> {
            if (item instanceof Document) {
                return documentToMap((Document) item);
            } else if (item instanceof List) {
                return convertList((List<?>) item);
            } else if (item instanceof ObjectId) {
                return ((ObjectId) item).toHexString();
            } else {
                return item;
            }
        }).collect(Collectors.toList());
    }

    /**
     * Convert List recursively (Java to BSON)
     */
    private List<Object> convertListToBson(List<?> list) {
        return list.stream().map(item -> {
            if (item instanceof Map) {
                return mapToDocument((Map<String, Object>) item);
            } else if (item instanceof List) {
                return convertListToBson((List<?>) item);
            } else {
                return item;
            }
        }).collect(Collectors.toList());
    }
}
