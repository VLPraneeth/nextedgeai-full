package com.syncari.core.service;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.DataFixQuery;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.DataFixQueryStatus;
import com.syncari.core.model.misc.DataFixQueryType;
import com.syncari.core.repositories.syncari.DataFixQueryRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;

/**
 * Integration tests for DataFixService approval and rejection workflow.
 * These tests verify the full flow including SyncariContext interactions.
 */
@Ignore
public class DataFixServiceIntegrationTest extends AbstractSyncariTest {

    @Autowired
    private DataFixService dataFixService;

    @Autowired
    private DataFixQueryRepo dataFixQueryRepo;

    @Autowired
    private UserRepo userRepo;

    private User requester;
    private User approver;
    private DataFixQuery testQuery;
    private User user;

    @Before
    public void setUp() {
        super.setUp();

        // Create requester user
        requester = new User();
        requester.setEmail("requester@test.com");
        requester.setFirstName("Requester");
        requester.setLastName("User");
        requester = userRepo.save(requester);

        // Create approver user
        approver = new User();
        approver.setEmail("approver@test.com");
        approver.setFirstName("Approver");
        approver.setLastName("User");
        approver = userRepo.save(approver);

        if(user == null) {
            user = SyncariContext.getUser();
        }

    }

    @After
    public void tearDown() {
        if (testQuery != null && testQuery.getId() != null) {
            dataFixQueryRepo.deleteById(testQuery.getId());
        }
        if (requester != null && requester.getId() != null) {
            userRepo.deleteById(requester.getId());
        }
        if (approver != null && approver.getId() != null) {
            userRepo.deleteById(approver.getId());
        }
        super.tearDown();
        SyncariContext.setUser(user);
    }

    @Test
    public void testFullApprovalWorkflow_Success() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        String queryText = "db.testCollection.updateMany({ status: 'pending' }, { $set: { status: 'active' } })";
        String justification = "Activate pending accounts";

        testQuery = dataFixService.submitForApproval(
                queryText,
                justification,
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Verify initial state
        assertNotNull(testQuery);
        assertNotNull(testQuery.getId());
        assertEquals(DataFixQueryStatus.PENDING_APPROVAL, testQuery.getStatus());
        assertEquals(requester.getId(), testQuery.getRequesterId());
        assertEquals(approver.getId(), testQuery.getApproverId());
        assertEquals(queryText, testQuery.getQueryText());
        assertEquals(justification, testQuery.getJustification());
        assertNotNull(testQuery.getSubmittedAt());

        // Approver approves the query
        SyncariContext.setUser(approver);
        String approvalNote = "Looks good, approved for execution";

        DataFixQuery approvedQuery = dataFixService.approveQuery(testQuery.getId(), approvalNote);

        // Verify approved state
        assertNotNull(approvedQuery);
        assertEquals(DataFixQueryStatus.APPROVED, approvedQuery.getStatus());
        assertEquals(approvalNote, approvedQuery.getApprovalNote());
        assertNotNull(approvedQuery.getApprovedAt());
        assertNull(approvedQuery.getRejectionReason());
        assertNull(approvedQuery.getRejectedAt());
    }

    @Test
    public void testFullRejectionWorkflow_Success() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        String queryText = "db.testCollection.deleteMany({ status: 'inactive' })";
        String justification = "Remove inactive accounts";

        testQuery = dataFixService.submitForApproval(
                queryText,
                justification,
                approver.getId(),
                DataFixQueryType.DELETE
        );

        // Verify initial state
        assertNotNull(testQuery);
        assertEquals(DataFixQueryStatus.PENDING_APPROVAL, testQuery.getStatus());

        // Approver rejects the query
        SyncariContext.setUser(approver);
        String rejectionReason = "Need more details about which accounts will be affected";

        DataFixQuery rejectedQuery = dataFixService.rejectQuery(testQuery.getId(), rejectionReason);

        // Verify rejected state
        assertNotNull(rejectedQuery);
        assertEquals(DataFixQueryStatus.REJECTED, rejectedQuery.getStatus());
        assertEquals(rejectionReason, rejectedQuery.getRejectionReason());
        assertNotNull(rejectedQuery.getRejectedAt());
        assertNull(rejectedQuery.getApprovalNote());
        assertNull(rejectedQuery.getApprovedAt());
    }

    @Test(expected = RuntimeException.class)
    public void testApproveQuery_MissingApprovalNote() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Approver tries to approve without approval note
        SyncariContext.setUser(approver);
        dataFixService.approveQuery(testQuery.getId(), ""); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testApproveQuery_MissingApprovalNote_Null() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Approver tries to approve with null approval note
        SyncariContext.setUser(approver);
        dataFixService.approveQuery(testQuery.getId(), null); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testRejectQuery_MissingRejectionReason() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Approver tries to reject without rejection reason
        SyncariContext.setUser(approver);
        dataFixService.rejectQuery(testQuery.getId(), ""); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testApproveQuery_WrongApprover() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Different user tries to approve (not the assigned approver)
        User otherUser = new User();
        otherUser.setId("other-user-id");
        otherUser.setEmail("other@test.com");
        SyncariContext.setUser(otherUser);

        dataFixService.approveQuery(testQuery.getId(), "Approval note"); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testRejectQuery_WrongApprover() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Different user tries to reject (not the assigned approver)
        User otherUser = new User();
        otherUser.setId("other-user-id");
        otherUser.setEmail("other@test.com");
        SyncariContext.setUser(otherUser);

        dataFixService.rejectQuery(testQuery.getId(), "Rejection reason"); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testApproveQuery_AlreadyApproved() {
        // Setup: Requester submits and approver approves query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        SyncariContext.setUser(approver);
        dataFixService.approveQuery(testQuery.getId(), "First approval");

        // Try to approve again
        dataFixService.approveQuery(testQuery.getId(), "Second approval"); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testRejectQuery_AlreadyRejected() {
        // Setup: Requester submits and approver rejects query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        SyncariContext.setUser(approver);
        dataFixService.rejectQuery(testQuery.getId(), "First rejection");

        // Try to reject again
        dataFixService.rejectQuery(testQuery.getId(), "Second rejection"); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testSubmitForApproval_SelfApproval() {
        // Setup: Requester tries to set themselves as approver
        SyncariContext.setUser(requester);

        dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                requester.getId(), // Same as requester
                DataFixQueryType.UPDATE
        ); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testSubmitForApproval_BlankJustification() {
        // Setup: Requester tries to submit without justification
        SyncariContext.setUser(requester);

        dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "", // Blank justification
                approver.getId(),
                DataFixQueryType.UPDATE
        ); // Should throw exception
    }

    @Test(expected = RuntimeException.class)
    public void testSubmitForApproval_BlacklistedCollection() {
        // Setup: Requester tries to query blacklisted collection
        SyncariContext.setUser(requester);

        dataFixService.submitForApproval(
                "db.auditLog.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        ); // Should throw exception for blacklisted collection
    }

    @Test
    public void testGetPendingApprovals_OnlyShowsAssignedQueries() {
        // Setup: Requester submits query for specific approver
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Approver checks their pending approvals
        var pendingApprovals = dataFixService.getPendingApprovals(approver.getId());

        assertNotNull(pendingApprovals);
        assertEquals(1, pendingApprovals.size());
        assertEquals(testQuery.getId(), pendingApprovals.get(0).getId());

        // Other user should not see this query
        var otherUserPending = dataFixService.getPendingApprovals("other-user-id");
        assertEquals(0, otherUserPending.size());
    }

    @Test
    public void testGetQueriesByRequester_OnlyShowsOwnQueries() {
        // Setup: Requester submits query
        SyncariContext.setUser(requester);

        testQuery = dataFixService.submitForApproval(
                "db.testCollection.updateMany({}, { $set: { flag: true } })",
                "Test justification",
                approver.getId(),
                DataFixQueryType.UPDATE
        );

        // Requester checks their queries
        var myQueries = dataFixService.getQueriesByRequester(requester.getId());

        assertNotNull(myQueries);
        assertEquals(1, myQueries.size());
        assertEquals(testQuery.getId(), myQueries.get(0).getId());

        // Other user should not see this query
        var otherUserQueries = dataFixService.getQueriesByRequester("other-user-id");
        assertEquals(0, otherUserQueries.size());
    }
}
