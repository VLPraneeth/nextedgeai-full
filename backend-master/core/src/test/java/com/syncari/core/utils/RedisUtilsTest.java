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
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import redis.clients.jedis.JedisPooled;

import java.util.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@Slf4j
@Ignore
public class RedisUtilsTest extends AbstractSyncariTest {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ConnectorService connectorService;

    @Autowired
    private SchemaService schemaService;

    EntityDefinition entityDefinition;

    @Before
    public void setUp(){
        super.setUp();
        SchemaHelper accountDef = SchemaHelper.createEntityDefinition("redisutils_account", connectorService.getSyncariConnector())
                .id();
        for (int i = 0; i < 2; i++) {
            accountDef = accountDef.string("field" + i);
        }
        accountDef.field("dtfield", DateType.VALUE);
        accountDef.field("boolfield", BooleanType.VALUE);
        accountDef.field("numField", IntegerType.VALUE);
        accountDef.field("dblField", DoubleType.VALUE);
        accountDef.datetime("dttimefield");
        entityDefinition = accountDef.getEntityDefinition();
        schemaService.upsertEntity(entityDefinition);
    }

    @After
    public void tearDown(){
        schemaService.deleteEntity(entityDefinition.getId());
        super.tearDown();
    }

    @Test
    public void constructAndExistsIndexTests() {
        List<AttributeDefinition> attributeDefinitions = entityDefinition.getAttributes();
        String indexName = "syncari_00XXBB_"+ entityDefinition.getApiName()+ "_idx";
        if (redisUtils.indexExists(indexName)){
            redisUtils.dropIndex(indexName);
        }
        List<CacheIndexAttribute> attributes = new ArrayList<>();
        attributeDefinitions.forEach(x -> {
            CacheIndexAttribute attrib = new CacheIndexAttribute().setPath(x.getApiName()).setAlias(x.getApiName()).setDataType(x.getDataType());
            attributes.add(attrib);
        });
        redisUtils.constructPipelineIndex("00XXBB",entityDefinition,  attributes);
        assertTrue(redisUtils.indexExists(indexName));
    }

    @Test
    public void constructAndAlterIndexTests() {
        List<AttributeDefinition> attributeDefinitions = entityDefinition.getAttributes();
        String indexName = this.getIndex(entityDefinition.getApiName());
        if (redisUtils.indexExists(indexName)){
            redisUtils.dropIndex(indexName);
        }
        List<CacheIndexAttribute> attributes = new ArrayList<>();
        List<String> attribApiNamesLikeIndex = new ArrayList<>();
        attributeDefinitions.forEach(x -> {
            CacheIndexAttribute attrib = new CacheIndexAttribute().setPath(x.getApiName()).setAlias(x.getApiName()).setDataType(x.getDataType());
            attribApiNamesLikeIndex.add("$." +x.getApiName());
            attributes.add(attrib);
        });
        redisUtils.constructOrAlterIndex(SyncariContext.getSyncariId(), entityDefinition,  attributes);
        Map<String, Object> indexInfo = redisUtils.getIndexInfo(getIndex(entityDefinition.getApiName()));
        List<Object> indxInfo = (List)indexInfo.getOrDefault("attributes", List.of());

        List<String> indxAttributeApiNames = new ArrayList<>();
        indxInfo.forEach(i -> indxAttributeApiNames.add(((List)i).get(1).toString()));
        indxAttributeApiNames.removeAll(attribApiNamesLikeIndex);
        assertTrue(CollectionUtils.isEmpty(indxAttributeApiNames));

        redisUtils.constructOrAlterIndex(SyncariContext.getSyncariId(), entityDefinition,  attributes);

        // calculate indexes attribs again
        indxInfo = (List)indexInfo.getOrDefault("attributes", List.of());
        List<String> indxAttributeApiNames2 = new ArrayList<>();
        indxInfo.forEach(i -> indxAttributeApiNames2.add(((List)i).get(1).toString()));

        indxAttributeApiNames2.removeAll(attribApiNamesLikeIndex);
        assertTrue(CollectionUtils.isEmpty(indxAttributeApiNames2));

    }

    @Test
    public void indexStatus() {

        redisUtils.redisClient = mock(JedisPooled.class);

        Map<String, Object> indexInfo = Map.of("num_docs", 200, "hash_indexing_failures", 30);

        when(redisUtils.redisClient.ftInfo(any())).thenReturn(indexInfo);

        boolean indexStatus = redisUtils.indexStatus(entityDefinition.getApiName());

        assertFalse(indexStatus);

        indexInfo = Map.of("num_docs", 200, "hash_indexing_failures", 1);

        when(redisUtils.redisClient.ftInfo(any())).thenReturn(indexInfo);
        indexStatus = redisUtils.indexStatus(entityDefinition.getApiName());

        assertTrue(indexStatus);
    }


    private String getIndex(String entity) {
        return String.format(redisUtils.INDEX_FORMAT, SyncariContext.getSyncariId(), entity);
    }


}
