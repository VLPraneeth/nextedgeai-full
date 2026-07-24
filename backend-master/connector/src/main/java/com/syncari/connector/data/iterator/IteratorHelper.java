package com.syncari.connector.data.iterator;

import org.springframework.stereotype.Component;

import com.syncari.connector.EntityData;
import com.syncari.connector.data.AttributeSchema;

@Component
public class IteratorHelper {

    public long getWatermarkValue(EntityData entityData, AttributeSchema watermarkField) {
        if (watermarkField == null)
            throw new RuntimeException(String.format("Watermark field not set on %s", entityData.getName()));
        String watermarkValue = entityData.getValueAsString(watermarkField.getApiName());
        long watermark = -1l;
        switch (watermarkField.getDataType()) {
            case "timestamp":
            case "number":
                watermark = Long.parseLong(watermarkValue);
                break;
        }
        return watermark;
    }
}
