package com.syncari.connector.mongodb;

import com.mongodb.client.MongoCollection;
import com.syncari.connector.data.AttributeSchema;
import com.syncari.connector.data.EntitySchema;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class MongoDBSchemaInferrer {

    private static final int DEFAULT_SAMPLE_SIZE = 100;
    private static final int DEFAULT_STRING_LENGTH = 255;
    private static final int DEFAULT_PRECISION = 19;
    private static final int DEFAULT_SCALE = 4;

    // Watermark field candidates in priority order
    private static final List<String> WATERMARK_CANDIDATES = Arrays.asList(
            "updatedAt", "updated_at", "modifiedAt", "modified_at", "lastModified", "last_modified",
            "createdAt", "created_at", "insertedAt", "inserted_at", "created", "inserted"
    );

    /**
     * Infer schema from a MongoDB collection by sampling documents
     */
    public EntitySchema inferSchema(MongoCollection<Document> collection, String collectionName) {
        return inferSchema(collection, collectionName, DEFAULT_SAMPLE_SIZE);
    }

    /**
     * Infer schema from a MongoDB collection with specified sample size
     */
    public EntitySchema inferSchema(MongoCollection<Document> collection, String collectionName, int sampleSize) {
        log.info("Inferring schema for collection: {} with sample size: {}", collectionName, sampleSize);

        EntitySchema schema = new EntitySchema();
        schema.setApiName(collectionName);
        schema.setDisplayName(collectionName);
        schema.setPluralName(collectionName);

        // Sample documents to infer field types
        Map<String, FieldTypeInfo> fieldTypes = new HashMap<>();
        int docCount = 0;

        try {
            // Sample recent documents (sorted by _id descending for performance)
            for (Document doc : collection.find().sort(new Document("_id", -1)).limit(sampleSize)) {
                docCount++;
                analyzeDocument(doc, fieldTypes);
            }

            log.info("Analyzed {} documents from collection: {}", docCount, collectionName);

            // Build AttributeSchemas from inferred types
            List<AttributeSchema> attributes = new ArrayList<>();

            for (Map.Entry<String, FieldTypeInfo> entry : fieldTypes.entrySet()) {
                String fieldName = entry.getKey();
                FieldTypeInfo typeInfo = entry.getValue();

                AttributeSchema attr = buildAttributeSchema(fieldName, typeInfo, docCount);
                attributes.add(attr);

                // Mark _id as ID field
                if ("_id".equals(fieldName)) {
                    attr.setIdField(true);
                    attr.setNillable(false);
                    attr.setUpdateable(false);
                }
            }

            schema.setAttributes(attributes);

            // Auto-detect watermark field
            detectWatermarkField(schema);

        } catch (Exception e) {
            log.error("Failed to infer schema for collection {}: {}", collectionName, e.getMessage(), e);
        }

        return schema;
    }

    /**
     * Analyze a document and update field type information
     */
    private void analyzeDocument(Document doc, Map<String, FieldTypeInfo> fieldTypes) {
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            FieldTypeInfo typeInfo = fieldTypes.computeIfAbsent(fieldName, k -> new FieldTypeInfo());
            typeInfo.incrementOccurrences();

            if (value != null) {
                String inferredType = inferBsonType(value);
                typeInfo.addType(inferredType);
            } else {
                typeInfo.setHasNullValues(true);
            }
        }
    }

    /**
     * Infer BSON type from value
     */
    private String inferBsonType(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Integer || value instanceof Long) {
            return "integer";
        } else if (value instanceof Double || value instanceof Float || value instanceof Decimal128) {
            return "double";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Date) {
            return "timestamp";
        } else if (value instanceof ObjectId) {
            return "objectId";
        } else if (value instanceof Document) {
            return "object";
        } else if (value instanceof List) {
            return "array";
        } else if (value instanceof byte[]) {
            return "binary";
        } else {
            return "string"; // Default to string for unknown types
        }
    }

    /**
     * Build AttributeSchema from field type info
     */
    private AttributeSchema buildAttributeSchema(String fieldName, FieldTypeInfo typeInfo, int totalDocs) {
        String dataType = determineDataType(typeInfo);
        boolean isNullable = typeInfo.hasNullValues || typeInfo.occurrences < totalDocs;

        AttributeSchema attr = new AttributeSchema();
        attr.setApiName(fieldName);
        attr.setDisplayName(formatDisplayName(fieldName));
        attr.setDataType(dataType);
        attr.setNillable(isNullable);
        attr.setUpdateable(true);

        // Set type-specific properties
        switch (dataType) {
            case "id":
                attr.setLength(24); // ObjectId hex string length
                break;
            case "string":
                attr.setLength(DEFAULT_STRING_LENGTH);
                break;
            case "integer":
                attr.setPrecision(19);
                break;
            case "double":
                attr.setPrecision(DEFAULT_PRECISION);
                attr.setScale(DEFAULT_SCALE);
                break;
            case "array":
                attr.setMultiValueField(true);
                break;
        }

        return attr;
    }

    /**
     * Determine final data type from type info
     */
    private String determineDataType(FieldTypeInfo typeInfo) {
        Set<String> types = typeInfo.getTypes();

        // Special case: if field is always objectId, treat as "id" type
        if (types.size() == 1 && types.contains("objectId")) {
            return "id";
        }

        // If single consistent type, use it
        if (types.size() == 1) {
            String type = types.iterator().next();
            switch (type) {
                case "objectId":
                    return "string"; // Convert ObjectId to string for display
                case "binary":
                    return "string"; // Binary data as base64 string
                default:
                    return type;
            }
        }

        // If mixed numeric types, prefer double
        if (types.contains("double") || types.contains("integer")) {
            if (types.contains("double")) {
                return "double";
            }
            if (types.size() == 1) {
                return "integer";
            }
        }

        // If mixed types, default to string (most flexible)
        return "string";
    }

    /**
     * Format field name for display
     */
    private String formatDisplayName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        // Handle snake_case
        if (fieldName.contains("_")) {
            String[] parts = fieldName.split("_");
            StringBuilder display = new StringBuilder();
            for (String part : parts) {
                if (display.length() > 0) {
                    display.append(" ");
                }
                display.append(capitalize(part));
            }
            return display.toString();
        }

        // Handle camelCase
        String result = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
        return capitalize(result);
    }

    /**
     * Capitalize first letter
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Auto-detect watermark field from schema
     */
    private void detectWatermarkField(EntitySchema schema) {
        // First, look for common timestamp field names
        for (String candidate : WATERMARK_CANDIDATES) {
            Optional<AttributeSchema> field = schema.getField(candidate);
            if (field.isPresent() && isTimestampType(field.get().getDataType())) {
                field.get().setWatermarkField(true);
                field.get().setUpdatedAtField(candidate.toLowerCase().contains("updated") ||
                        candidate.toLowerCase().contains("modified"));
                field.get().setCreatedAtField(candidate.toLowerCase().contains("created") ||
                        candidate.toLowerCase().contains("inserted"));
                log.info("Auto-detected watermark field: {}", candidate);
                return;
            }
        }

        // Fallback: use _id if it exists (ObjectId contains creation timestamp)
        Optional<AttributeSchema> idField = schema.getField("_id");
        if (idField.isPresent()) {
            idField.get().setWatermarkField(true);
            idField.get().setCreatedAtField(true);
            log.info("Using _id as watermark field (fallback)");
        }
    }

    /**
     * Check if data type is timestamp-related
     */
    private boolean isTimestampType(String dataType) {
        return "timestamp".equals(dataType) || "datetime".equals(dataType) || "date".equals(dataType);
    }

    /**
     * Inner class to track field type information during analysis
     */
    private static class FieldTypeInfo {
        private Set<String> types = new HashSet<>();
        private int occurrences = 0;
        private boolean hasNullValues = false;

        void addType(String type) {
            types.add(type);
        }

        void incrementOccurrences() {
            occurrences++;
        }

        void setHasNullValues(boolean hasNull) {
            this.hasNullValues = hasNull;
        }

        Set<String> getTypes() {
            return types;
        }
    }
}
