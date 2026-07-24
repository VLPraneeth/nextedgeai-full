package com.syncari.core.utils;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DoubleType;
import com.syncari.core.datatype.IntegerType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.cache.CacheIndexAttribute;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.search.querybuilder.Value;
import redis.clients.jedis.search.querybuilder.Values;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
public class RedisValuesTest {

   @Test
   public void escapeTest() {
       assertEquals("{test}", RedisValues.escapedTags("test").toString());
       assertEquals("{john\\@syncari\\.com}", RedisValues.escapedTags("john@syncari.com").toString());
       assertEquals("{Melroses\\'s\\ Place}", RedisValues.escapedTags("Melroses's Place").toString());
        assertEquals("{This\\ is\\ a\\ qoute\\ \\\"Good\\ Quote\\\"}", RedisValues.escapedTags("This is a qoute \"Good Quote\"").toString());
   }

    @Test
    public void escapeTransformTest() {

       Function<String, String> transform = s ->  String.format("*%s*", s);
       assertEquals("{*test*}", RedisValues.escapedTags(transform, "test").toString());
       assertEquals("{*john\\@syncari\\.com*}", RedisValues.escapedTags(transform, "john@syncari.com").toString());

       Function<String, String> transform1 = s ->  String.format("%s*", s);

       assertEquals("{test*}", RedisValues.escapedTags(transform1, "test").toString());
       assertEquals("{john\\@syncari\\.com*}", RedisValues.escapedTags(transform1, "john@syncari.com").toString());
    }


}
