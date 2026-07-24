package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.DataFixApprovalRequest;
import com.syncari.api.rest.controllers.data.DataFixDryRunRequest;
import com.syncari.api.rest.controllers.data.DataFixQueryRequest;
import com.syncari.api.rest.controllers.data.DataFixQueryResponse;
import com.syncari.api.rest.controllers.data.DataFixReadQueryRequest;
import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import com.syncari.core.service.DataFixService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.*;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DataFixControllerTest {

    @Mock
    private DataFixService dataFixService;

    @InjectMocks
    private DataFixController dataFixController;

    private DataFixQuery sampleQuery;
    private Map<String, Object> sampleResult;

    @Before
    public void setUp() {
        // Create sample query
        sampleQuery = new DataFixQuery(
                "db.accounts.updateMany({ _id: '123' }, { $set: { status: 'active' } })",
                DataFixQueryType.UPDATE,
                "Test justification"
        );
        sampleQuery.setId("query-123");
        sampleQuery.setRequesterId("user-1");
        sampleQuery.setRequesterEmail("user@test.com");
        sampleQuery.setApproverId("user-2");
        sampleQuery.setApproverEmail("approver@test.com");
        sampleQuery.setStatus(DataFixQueryStatus.PENDING_APPROVAL);
        sampleQuery.setTargetCollection("accounts");
        sampleQuery.setSubmittedAt(new Date());

        // Create sample result
        sampleResult = new HashMap<>();
        sampleResult.put("results", Arrays.asList(new HashMap<String, Object>()));
        sampleResult.put("rowCount", 1);
        sampleResult.put("limited", false);
    }

    @Test
    public void testExecuteReadQuery() {
        // Arrange
        DataFixReadQueryRequest request = new DataFixReadQueryRequest();
        request.setQueryText("db.accounts.find({})");
        request.setTargetDatabase("customer-db");

        when(dataFixService.executeReadQuery(anyString(), anyString())).thenReturn(sampleResult);

        // Act
        Map<String, Object> result = dataFixController.executeReadQuery(request);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.get("rowCount"));
        verify(dataFixService, times(1)).executeReadQuery("db.accounts.find({})", "customer-db");
    }

    @Test
    public void testSubmitUpdateQuery() {
        // Arrange
        DataFixQueryRequest request = new DataFixQueryRequest();
        request.setQueryText("db.accounts.updateMany({ _id: '123' }, { $set: { status: 'active' } })");
        request.setJustification("Update account status");
        request.setApproverId("user-2");
        request.setQueryType(DataFixQueryType.UPDATE);

        when(dataFixService.submitForApproval(anyString(), anyString(), anyString(), any(DataFixQueryType.class)))
                .thenReturn(sampleQuery);

        // Act
        DataFixQueryResponse response = dataFixController.submitUpdateQuery(request);

        // Assert
        assertNotNull(response);
        assertEquals("query-123", response.getId());
        assertEquals(DataFixQueryStatus.PENDING_APPROVAL, response.getStatus());
        verify(dataFixService, times(1)).submitForApproval(
                request.getQueryText(),
                request.getJustification(),
                request.getApproverId(),
                request.getQueryType()
        );
    }

    @Test
    public void testExecuteDryRunDirect() {
        // Arrange
        DataFixDryRunRequest request = new DataFixDryRunRequest();
        request.setQueryText("db.accounts.updateMany({ _id: '123' }, { $set: { status: 'active' } })");
        request.setQueryType(DataFixQueryType.UPDATE);

        Map<String, Object> dryRunResult = new HashMap<>();
        dryRunResult.put("affectedRows", 5);
        dryRunResult.put("sampleRecords", new ArrayList<>());

        when(dataFixService.executeDryRunDirect(anyString(), any(DataFixQueryType.class)))
                .thenReturn(dryRunResult);

        // Act
        Map<String, Object> result = dataFixController.executeDryRunDirect(request);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.get("affectedRows"));
        verify(dataFixService, times(1)).executeDryRunDirect(request.getQueryText(), request.getQueryType());
    }

    @Test
    public void testApproveQuery() {
        // Arrange
        String queryId = "query-123";
        DataFixApprovalRequest request = new DataFixApprovalRequest();
        request.setApprovalNote("Looks good, approved");

        DataFixQuery approvedQuery = sampleQuery;
        approvedQuery.setStatus(DataFixQueryStatus.APPROVED);
        approvedQuery.setApprovalNote("Looks good, approved");
        approvedQuery.setApprovedAt(new Date());

        when(dataFixService.approveQuery(anyString(), anyString())).thenReturn(approvedQuery);

        // Act
        DataFixQueryResponse response = dataFixController.approveQuery(queryId, request);

        // Assert
        assertNotNull(response);
        assertEquals(DataFixQueryStatus.APPROVED, response.getStatus());
        assertEquals("Looks good, approved", response.getApprovalNote());
        verify(dataFixService, times(1)).approveQuery(queryId, "Looks good, approved");
    }

    @Test
    public void testRejectQuery() {
        // Arrange
        String queryId = "query-123";
        DataFixApprovalRequest request = new DataFixApprovalRequest();
        request.setRejectionReason("Does not meet requirements");

        DataFixQuery rejectedQuery = sampleQuery;
        rejectedQuery.setStatus(DataFixQueryStatus.REJECTED);
        rejectedQuery.setRejectionReason("Does not meet requirements");
        rejectedQuery.setRejectedAt(new Date());

        when(dataFixService.rejectQuery(anyString(), anyString())).thenReturn(rejectedQuery);

        // Act
        DataFixQueryResponse response = dataFixController.rejectQuery(queryId, request);

        // Assert
        assertNotNull(response);
        assertEquals(DataFixQueryStatus.REJECTED, response.getStatus());
        assertEquals("Does not meet requirements", response.getRejectionReason());
        verify(dataFixService, times(1)).rejectQuery(queryId, "Does not meet requirements");
    }

    @Test
    public void testExecuteApprovedQuery() {
        // Arrange
        String queryId = "query-123";
        Map<String, Object> executionResult = new HashMap<>();
        executionResult.put("modifiedCount", 5);
        executionResult.put("matchedCount", 5);

        when(dataFixService.executeApprovedQuery(anyString())).thenReturn(executionResult);

        // Act
        Map<String, Object> result = dataFixController.executeApprovedQuery(queryId);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.get("modifiedCount"));
        verify(dataFixService, times(1)).executeApprovedQuery(queryId);
    }

    @Ignore
    @Test
    public void testGetMyQueries() {
        // Arrange
        List<DataFixQuery> queries = Arrays.asList(sampleQuery);
        when(dataFixService.getQueriesByRequester(anyString())).thenReturn(queries);

        // Act
        List<DataFixQueryResponse> result = dataFixController.getMyQueries();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("query-123", result.get(0).getId());
    }

    @Ignore
    @Test
    public void testGetPendingApprovals() {
        // Arrange
        List<DataFixQuery> queries = Arrays.asList(sampleQuery);
        when(dataFixService.getPendingApprovals(anyString())).thenReturn(queries);

        // Act
        List<DataFixQueryResponse> result = dataFixController.getPendingApprovals();

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(DataFixQueryStatus.PENDING_APPROVAL, result.get(0).getStatus());
    }

    @Test
    public void testGetQuery() {
        // Arrange
        String queryId = "query-123";
        when(dataFixService.getQueryById(anyString())).thenReturn(Optional.of(sampleQuery));

        // Act
        DataFixQueryResponse response = dataFixController.getQuery(queryId);

        // Assert
        assertNotNull(response);
        assertEquals("query-123", response.getId());
        verify(dataFixService, times(1)).getQueryById(queryId);
    }

    @Test
    public void testGetAllQueries() {
        // Arrange
        List<DataFixQuery> queries = Arrays.asList(sampleQuery);
        when(dataFixService.getAllQueries()).thenReturn(queries);

        // Act
        List<DataFixQueryResponse> result = dataFixController.getAllQueries(null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(dataFixService, times(1)).getAllQueries();
    }

    @Test
    public void testGetQueriesByStatus() {
        // Arrange
        List<DataFixQuery> queries = Arrays.asList(sampleQuery);
        when(dataFixService.getQueriesByStatus(any(DataFixQueryStatus.class))).thenReturn(queries);

        // Act
        List<DataFixQueryResponse> result = dataFixController.getAllQueries(DataFixQueryStatus.PENDING_APPROVAL);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(DataFixQueryStatus.PENDING_APPROVAL, result.get(0).getStatus());
        verify(dataFixService, times(1)).getQueriesByStatus(DataFixQueryStatus.PENDING_APPROVAL);
    }

    @Test
    public void testGetCollectionNames() {
        // Arrange
        List<String> collections = Arrays.asList("accounts", "contacts", "opportunities");
        when(dataFixService.getCollectionNames()).thenReturn(collections);

        // Act
        List<String> result = dataFixController.getCollectionNames();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains("accounts"));
        assertTrue(result.contains("contacts"));
        verify(dataFixService, times(1)).getCollectionNames();
    }

    @Test(expected = RuntimeException.class)
    public void testGetQueryNotFound() {
        // Arrange
        String queryId = "non-existent";
        when(dataFixService.getQueryById(anyString())).thenReturn(Optional.empty());

        // Act
        dataFixController.getQuery(queryId);

        // Should throw RuntimeException
    }
}
