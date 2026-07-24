package com.syncari.core.utils;

import com.syncari.core.service.cache.CacheDataTypeConverter;
import org.bson.conversions.Bson;
import redis.clients.jedis.search.querybuilder.Node;
import redis.clients.jedis.search.querybuilder.Value;

import java.util.List;
import java.util.Optional;

public interface RedisCriteria extends Criteria<Node> {

    Node createCriteria();

    default List<LookupCriteriaVisitor.Sort> sort(){
        return List.of();
    }

    CacheDataTypeConverter converter = new CacheDataTypeConverter();

    default Value convertValue(Object value) {
        return converter.convertFrom(value);
    }

}
