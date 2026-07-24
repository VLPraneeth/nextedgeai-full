package com.syncari.core.utils;

import org.bson.conversions.Bson;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

public interface Criteria<T> {
    T createCriteria();
    //boolean foundEmptyValuedPredicates();
}
