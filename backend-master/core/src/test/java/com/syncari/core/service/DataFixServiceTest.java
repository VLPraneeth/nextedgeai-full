package com.syncari.core.service;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import com.syncari.core.repositories.syncari.DataFixQueryRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DataFixServiceTest {

    @Mock
    private DataFixQueryRepo dataFixQueryRepo;

    @Mock
    private DataFixAuditService auditService;

    @Mock
    private MongoTemplate customerMongoTemplate;

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepo userRepo;

    @InjectMocks
    private DataFixService dataFixService;

    private User testUser;
    private User approverUser;

    @Before
    public void setUp() {
        testUser = new User();
        testUser.setId("user-1");
        testUser.setEmail("test@example.com");

        approverUser = new User();
        approverUser.setId("user-2");
        approverUser.setEmail("approver@example.com");
    }

    // Note: Tests for submitForApproval, approveQuery, and rejectQuery require
    // SyncariContext static mocking which is not available in this version of Mockito.
    // These methods should be tested via integration tests instead.

    @Test
    public void testGetQueryById_Success() {
        // Arrange
        String queryId = "query-123";
        DataFixQuery query = new DataFixQuery(
                "db.accounts.find({})",
                DataFixQueryType.READ,
                "Test"
        );
        query.setId(queryId);

        when(dataFixQueryRepo.findById(queryId)).thenReturn(Optional.of(query));

        // Act
        Optional<DataFixQuery> result = dataFixService.getQueryById(queryId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(queryId, result.get().getId());
        verify(dataFixQueryRepo, times(1)).findById(queryId);
    }

    @Test
    public void testGetQueriesByRequester() {
        // Arrange
        String userId = "user-1";
        List<DataFixQuery> queries = Arrays.asList(
                new DataFixQuery("db.accounts.find({})", DataFixQueryType.READ, "Test")
        );

        when(dataFixQueryRepo.findByRequesterId(userId)).thenReturn(queries);

        // Act
        List<DataFixQuery> result = dataFixService.getQueriesByRequester(userId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(dataFixQueryRepo, times(1)).findByRequesterId(userId);
    }

    @Test
    public void testGetAllQueries() {
        // Arrange
        List<DataFixQuery> queries = Arrays.asList(
                new DataFixQuery("db.accounts.find({})", DataFixQueryType.READ, "Test")
        );

        when(dataFixQueryRepo.findAll()).thenReturn(queries);

        // Act
        List<DataFixQuery> result = dataFixService.getAllQueries();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(dataFixQueryRepo, times(1)).findAll();
    }

    @Test
    public void testGetQueriesByStatus() {
        // Arrange
        DataFixQueryStatus status = DataFixQueryStatus.APPROVED;
        List<DataFixQuery> queries = Arrays.asList(
                new DataFixQuery("db.accounts.updateMany({})", DataFixQueryType.UPDATE, "Test")
        );

        when(dataFixQueryRepo.findByStatus(status)).thenReturn(queries);

        // Act
        List<DataFixQuery> result = dataFixService.getQueriesByStatus(status);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(dataFixQueryRepo, times(1)).findByStatus(status);
    }

    @Test
    public void testGetCollectionNames_FilterBlacklisted() {
        // Arrange
        MongoDatabase mockDb = mock(MongoDatabase.class);
        org.springframework.data.mongodb.MongoDbFactory mockFactory = mock(org.springframework.data.mongodb.MongoDbFactory.class);
        when(customerMongoTemplate.getMongoDbFactory()).thenReturn(mockFactory);
        when(mockFactory.getDb()).thenReturn(mockDb);

        List<String> allCollections = new ArrayList<>();
        allCollections.add("accounts");
        allCollections.add("auditLog");
        allCollections.add("dataFixAuditLog");
        allCollections.add("contacts");
        allCollections.add("system.indexes");

        com.mongodb.client.MongoIterable<String> mockIterable = mock(com.mongodb.client.MongoIterable.class);
        when(mockDb.listCollectionNames()).thenReturn(mockIterable);
        when(mockIterable.into(anyList())).thenAnswer(invocation -> {
            List<String> list = invocation.getArgument(0);
            list.addAll(allCollections);
            return list;
        });

        // Act
        List<String> result = dataFixService.getCollectionNames();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("accounts"));
        assertTrue(result.contains("contacts"));
        assertFalse(result.contains("auditLog"));
        assertFalse(result.contains("dataFixAuditLog"));
        assertFalse(result.contains("system.indexes"));
    }
}
