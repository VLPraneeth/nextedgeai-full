package com.syncari.core.utils;

import org.bson.conversions.Bson;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

public interface MongoCriteria extends Criteria<Bson> {
    Bson createCriteria();
    default Optional<Bson> sort(){
        return Optional.empty();
    }
    default boolean hasCaseInsensitiveIndexField() {
        return false;
    }
    default Object getDataTypeSpecificConversion(Object converted) {
        if(converted == null) return converted;
        if(converted instanceof ZonedDateTime) {
            return Date.from(((ZonedDateTime)converted).toInstant());
        }
        return converted;
    }
}
