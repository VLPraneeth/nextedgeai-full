package com.syncari.core.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.bson.Document;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.Index;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.SearchCriteria;
import com.syncari.core.repositories.customer.EntityRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongoUtilsTest extends AbstractSyncariTest {
	@Autowired
	CustomerMongoUtils customerMongoUtils;
	@Autowired
	MongoDbFactory customerDBFactory;
    @Autowired
    MongoTemplate customerMongoTemplate;
    @Autowired
    EntityRepo entityRepo;

	@Test
	public void createCustomerCollectionValidations() {
		try {
			customerMongoUtils.createCollection(null, null);
			fail();
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Collection name is required"));
		}
	}

	@Test
	public void createCustomerCollection() {
		customerMongoUtils.createCollection("test_collection", List.of("test_field"));
		MongoDatabase db = customerDBFactory.getDb(SyncariContext.getDatabase());
		MongoIterable<String> listCollectionNames = db.listCollectionNames();
		MongoCursor<String> iterator = listCollectionNames.iterator();
		while(iterator.hasNext()) {
			String name = iterator.next();
			if ("test_collection".equalsIgnoreCase(name)) {
				MongoCollection<Document> coll = db.getCollection("test_collection");
				coll.drop();
				return;
			}
		}
		fail();
	}

    @Test
    public void indexesTest() {
        customerMongoUtils.createCollection("test_index_collection", List.of("test_index_field", "test_i_index_field", "test_non_index_field", "test_index_field_order1","test_index_field_order2"));
        MongoDatabase db = customerDBFactory.getDb(SyncariContext.getDatabase());
        MongoUtils.createIndexes(customerMongoTemplate, "test_index_collection", List.of(
                new Index("test_index_collection_case_sensitive", true, true, "test_index_field")
        ));
        MongoUtils.createIndexes(customerMongoTemplate, "test_index_collection", List.of(
                new Index("test_index_collection_case_insensitive", true, false, "test_i_index_field")
        ));

        // Case sensitive index
        assertTrue(customerMongoUtils.hasIndexOnField("test_index_collection", "test_index_field"));
        assertFalse(customerMongoUtils.hasCaseInsensitiveIndexOnField("test_index_collection", "test_index_field"));

        // Case insensitive index
        assertTrue(customerMongoUtils.hasIndexOnField("test_index_collection", "test_i_index_field"));
        assertTrue(customerMongoUtils.hasCaseInsensitiveIndexOnField("test_index_collection", "test_i_index_field"));

        // No index
        assertFalse(customerMongoUtils.hasIndexOnField("test_index_collection", "test_non_index_field"));
        assertFalse(customerMongoUtils.hasCaseInsensitiveIndexOnField("test_index_collection", "test_non_index_field"));

        MongoUtils.createIndexes(customerMongoTemplate, "test_index_collection", List.of(
                new Index(false, Map.of("test_index_field_order2", -1), "test_index_field_order1", "test_index_field_order2")
        ));

        assertTrue(customerMongoUtils.hasIndexOnField("test_index_collection", "test_index_field_order1"));
        assertTrue(customerMongoUtils.hasIndexOnField("test_index_collection", "test_index_field_order2"));

        // Case insensitive index in composite index.
        assertTrue(customerMongoUtils.hasIndexOnField("entityDefinition", "connectorId"));
        assertTrue(customerMongoUtils.hasCaseInsensitiveIndexOnField("entityDefinition", "connectorId"));
        assertTrue(customerMongoUtils.hasIndexOnField("entityDefinition", "apiName"));
        assertTrue(customerMongoUtils.hasCaseInsensitiveIndexOnField("entityDefinition", "apiName"));
        assertTrue(customerMongoUtils.hasIndexOnField("entityDefinition", "draftStatus"));
        assertTrue(customerMongoUtils.hasCaseInsensitiveIndexOnField("entityDefinition", "draftStatus"));
        // No indexes on displayName.
        assertFalse(customerMongoUtils.hasIndexOnField("entityDefinition", "displayName"));
        assertFalse(customerMongoUtils.hasCaseInsensitiveIndexOnField("entityDefinition", "displayName"));

        customerMongoUtils.createCollection("test_lengthy_indx", List.of("syncari_some_lengthy_collection_exceeding_127_chars_indx"));
        MongoUtils.createIndexes(customerMongoTemplate, "test_lengthy_indx", List.of(
            new Index("syncari_some_lengthy_collection_exceeding_127_chars_indx", true, true, 
                "syncari_some_lengthy_collection_exceeding_127_chars_indx")
        ));
        assertTrue(customerMongoUtils.hasIndexOnField("test_lengthy_indx", "syncari_some_lengthy_collection_exceeding_127_chars_indx"));

        customerMongoUtils.createCollection("syncari_some_lengthy_collection_exceeding_127_chars_indx", List.of());
        customerMongoUtils.createFieldIndexes("syncari_some_lengthy_collection_exceeding_127_chars_indx", 
            List.of("syncariTimestamp"));
        assertTrue(customerMongoUtils.hasIndexOnField("syncari_some_lengthy_collection_exceeding_127_chars_indx", 
            "syncariTimestamp"));
    }

    @Test
    public void searchTests() {
        MongoDatabase db = customerDBFactory.getDb(SyncariContext.getDatabase());
        db.getCollection("search_test").drop();

        customerMongoUtils.createCollection("search_test", List.of("index_field", "ig_index_field", "non_index_field", "isDeleted"));
        
        MongoUtils.createIndexes(customerMongoTemplate, "search_test", List.of(
                new Index("test_index_collection_case_sensitive", false, true, "index_field")
        ));
        MongoUtils.createIndexes(customerMongoTemplate, "search_test", List.of(
                new Index("test_index_collection_case_insensitive", false, false, "ig_index_field")
        ));
        db.getCollection("search_test").insertOne(new Document(Map.of("index_field", "CaseSensitiveValue", 
            "ig_index_field", "CaseInsensitiveValue", "non_index_field", "NonIndexValue", "isDeleted", false)));
        db.getCollection("search_test").insertOne(new Document(Map.of("index_field", "casesensitivevalue", 
            "ig_index_field", "caseinsensitivevalue", "non_index_field", "nonindexvalue", "isDeleted", false)));
        db.getCollection("search_test").insertOne(new Document(Map.of("index_field", "CASESENSITIVEVALUE", 
            "ig_index_field", "CASEINSENSITIVEVALUE", "non_index_field", "NONINDEXVALUE", "isDeleted", false)));
        Iterator<Document> itr = db.getCollection("search_test").find().iterator();
        while (itr.hasNext()) {
            log.info("Found row {} ", itr.next());
        }

        // Case-Sensitive Index
        assertValueFound("search_test", "index_field", "CaseSensitiveValue", 1, true);
        assertValueFound("search_test", "index_field", "casesensitivevalue", 1, true);
        assertValueFound("search_test", "index_field", "CASESENSITIVEVALUE", 1, true);
        // Check 'Equals Ignore Case' but without the case insensitive index.
        assertValueFound("search_test", "index_field", "CaseSensitiveValue", 3, false);
        assertValueFound("search_test", "index_field", "casesensitivevalue", 3, false);
        assertValueFound("search_test", "index_field", "CASESENSITIVEVALUE", 3, false);

        // Case-Insensitive Index
        // Turn off case sensitive search "Equals Ignore Case" scenario
        assertValueFound("search_test", "ig_index_field", "CaseInsensitiveValue", 3, false);
        assertValueFound("search_test", "ig_index_field", "caseinsensitivevalue", 3, false);
        assertValueFound("search_test", "ig_index_field", "CASEINSENSITIVEVALUE", 3, false);
        // Turn on case sensitive search "Equals" scenario
        assertValueFound("search_test", "ig_index_field", "CaseInsensitiveValue", 1, true);
        assertValueFound("search_test", "ig_index_field", "caseinsensitivevalue", 1, true);
        assertValueFound("search_test", "ig_index_field", "CASEINSENSITIVEVALUE", 1, true);

        assertValueFound("search_test", "non_index_field", "NonIndexValue", 1, true);
        assertValueFound("search_test", "non_index_field", "nonindexvalue", 1, true);
        assertValueFound("search_test", "non_index_field", "NONINDEXVALUE", 1, true);

        db.getCollection("search_test").insertOne(new Document(Map.of("index_field", "सिंकर", 
            "ig_index_field", "सिंकर", "non_index_field", "सिंकर", "isDeleted", false)));
        assertValueFound("search_test", "index_field", "सिंकर", 1, true);
        assertValueFound("search_test", "index_field", "सिंकर", 1, false);

        assertValueFound("search_test", "ig_index_field", "सिंकर", 1, true);
        assertValueFound("search_test", "ig_index_field", "सिंकर", 1, false);

        assertValueFound("search_test", "non_index_field", "सिंकर", 1, true);
        assertValueFound("search_test", "non_index_field", "सिंकर", 1, false);

        db.getCollection("search_test").drop();
    }

    @Test
    public void deleteAll() {
        MongoDatabase db = customerDBFactory.getDb(SyncariContext.getDatabase());
        db.getCollection("delete_test").drop();

        customerMongoUtils.createCollection("delete_test", List.of());

        db.getCollection("delete_test").insertMany(List.of(
                new Document(Map.of("field1", "value11","field2","value21")),
                new Document(Map.of("field1", "value12","field2","value22")),
                new Document(Map.of("field1", "value13","field2","value23")),
                new Document(Map.of("field1", "value14","field2","value24"))
        ));

        assertEquals(4,customerMongoUtils.count("delete_test", Optional.empty()));
        customerMongoUtils.deleteAll("delete_test");
        assertEquals(0,customerMongoUtils.count("delete_test", Optional.empty()));
        db.getCollection("delete_test").drop();
    }
    private void assertValueFound(String collName, String fieldName, String value, int expectedCount, boolean caseSensitiveSearch) {
        Function<Document, String> converter = document -> document.toJson();
        Slice<String> search = customerMongoUtils.search(collName, 
            SearchCriteria.with(fieldName, value).setCaseSensitive(caseSensitiveSearch), PageRequest.of(0, 5), converter);
        assertEquals(expectedCount, search.getNumberOfElements());
    }

}
