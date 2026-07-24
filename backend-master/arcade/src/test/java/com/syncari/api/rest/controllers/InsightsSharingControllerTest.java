package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.insights.*;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.SharedItem;
import com.syncari.core.model.User;
import com.syncari.core.model.insights.sharing.InsightsDashboardSharedItem;
import com.syncari.core.model.insights.sharing.SharedItemInvitationStatus;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.service.InsightsSharingService;
import com.syncari.core.service.UserService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.*;

public class InsightsSharingControllerTest extends AbstractSyncariTest{

    @Autowired
    InsightsSharingController sharingController;

    @Autowired
    UserService userService;

    @Autowired
    InsightsSharingService service;

    @Test
    @WithMockUser(username = "test@email.com", authorities = {CREATE_ALLOWED_DOMAINS,READ_ALLOWED_DOMAINS,DELETE_ALLOWED_DOMAINS})
    public void testAllowedDomainsSaveAndList(){
        assertNull(sharingController.listAllowedDomains());
        InsightsSharingAllowedDomainDTO dto = new InsightsSharingAllowedDomainDTO().setDomains(List.of("test.com","gmail.com"));
        sharingController.addOrUpdateAllowedDomains(dto);
        InsightsSharingAllowedDomainDTO allowedDomain = sharingController.listAllowedDomains();

        assertNotNull(allowedDomain);
        assertEquals(true, allowedDomain.getDomains().contains("test.com"));
        assertEquals(true, allowedDomain.getDomains().contains("gmail.com"));
        assertEquals(2, allowedDomain.getDomains().size());
        sharingController.addOrUpdateAllowedDomains(dto);
        allowedDomain = sharingController.listAllowedDomains();
        assertNotNull(allowedDomain);
        assertEquals(2, allowedDomain.getDomains().size());
        sharingController.deleteAllAllowedDomains();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {CREATE_ALLOWED_DOMAINS,READ_ALLOWED_DOMAINS,DELETE_ALLOWED_DOMAINS,SHARE_DASHBOARD})
    public void testAllowedDomainsSaveAndDelete(){
        assertNull(sharingController.listAllowedDomains());
        InsightsSharingAllowedDomainDTO dto = new InsightsSharingAllowedDomainDTO().setDomains(List.of("test.com","gmail.com"));
        sharingController.addOrUpdateAllowedDomains(dto);
        InsightsSharingAllowedDomainDTO allowedDomain = sharingController.listAllowedDomains();

        assertNotNull(allowedDomain);
        assertEquals(true, allowedDomain.getDomains().contains("test.com"));
        assertEquals(true, allowedDomain.getDomains().contains("gmail.com"));
        assertEquals(2, allowedDomain.getDomains().size());

        Instant instant = Instant.now().plus(2, ChronoUnit.DAYS);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());

        List<InsightsShareDashboardResponseDTO> responseDTOS2 = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("gmail").setEmails(List.of("test@gmail.com"))
                .setExpiryDate(ldt.toString()).setMessage("gmail"));
        assertNotNull(responseDTOS2);
        assertFalse(CollectionUtils.isEmpty(responseDTOS2));
        assertEquals(1,responseDTOS2.size());
        assertNotNull(responseDTOS2.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("gmail",((InsightsDashboardSharedItem)responseDTOS2.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("gmail",responseDTOS2.stream().findFirst().get().getSharedItem().getSourceId());

        InsightsSharingAllowedDomainDTO dto2 = new InsightsSharingAllowedDomainDTO().setDomains(List.of("gmail.com"));
        sharingController.addOrUpdateAllowedDomains(dto2);
        allowedDomain = sharingController.listAllowedDomains();
        assertNotNull(allowedDomain);
        assertEquals(1, allowedDomain.getDomains().size());
        assertEquals("gmail.com", allowedDomain.getDomains().get(0));
        userService.findActiveUserByEmail("test@test.com").ifPresent(u -> {
            List<SharedItem> sharedItems = service.findAllSharedItemsByRecipientId(u.getId());
            assertTrue(CollectionUtils.isEmpty(sharedItems));
        });

        userService.findActiveUserByEmail("test@gmail.com").ifPresent(u -> {
            List<SharedItem> sharedItems = service.findAllSharedItemsByRecipientId(u.getId());
            assertTrue(CollectionUtils.isNotEmpty(sharedItems));
        });

        InsightsSharingAllowedDomainDTO dto3 = new InsightsSharingAllowedDomainDTO().setDomains(List.of());
        sharingController.addOrUpdateAllowedDomains(dto3);
        allowedDomain = sharingController.listAllowedDomains();
        assertNotNull(allowedDomain);
        assertEquals(0, allowedDomain.getDomains().size());

        userService.findActiveUserByEmail("test@gmail.com").ifPresent(u -> {
            List<SharedItem> sharedItems = service.findAllSharedItemsByRecipientId(u.getId());
            assertTrue(CollectionUtils.isNotEmpty(sharedItems));
        });

        InsightsSharingAllowedDomainDTO dto4 = new InsightsSharingAllowedDomainDTO().setDomains(List.of("test.com"));
        sharingController.addOrUpdateAllowedDomains(dto4);

        allowedDomain = sharingController.listAllowedDomains();
        assertNotNull(allowedDomain);
        assertEquals(1, allowedDomain.getDomains().size());

        userService.findActiveUserByEmail("test@gmail.com").ifPresent(u -> {
            List<SharedItem> sharedItems = service.findAllSharedItemsByRecipientId(u.getId());
            assertTrue(CollectionUtils.isEmpty(sharedItems));
        });
        sharingController.deleteAllAllowedDomains();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {CREATE_ALLOWED_DOMAINS,READ_ALLOWED_DOMAINS,DELETE_ALLOWED_DOMAINS,SHARE_DASHBOARD})
    public void testAllowedDomainsSaveAndDeleteFromAllToAddSome(){
        Instant instant = Instant.now().plus(2, ChronoUnit.DAYS);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());

        List<InsightsShareDashboardResponseDTO> responseDTOS2 = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("gmail").setEmails(List.of("test@gmail.com"))
                .setExpiryDate(ldt.toString()).setMessage("gmail"));
        assertNotNull(responseDTOS2);
        assertFalse(CollectionUtils.isEmpty(responseDTOS2));
        assertEquals(1,responseDTOS2.size());
        assertNotNull(responseDTOS2.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("gmail",((InsightsDashboardSharedItem)responseDTOS2.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("gmail",responseDTOS2.stream().findFirst().get().getSharedItem().getSourceId());

        assertNull(sharingController.listAllowedDomains());
        InsightsSharingAllowedDomainDTO dto = new InsightsSharingAllowedDomainDTO().setDomains(List.of("test.com"));
        sharingController.addOrUpdateAllowedDomains(dto);
        InsightsSharingAllowedDomainDTO allowedDomain = sharingController.listAllowedDomains();

        assertNotNull(allowedDomain);
        assertEquals(true, allowedDomain.getDomains().contains("test.com"));
        assertEquals(1, allowedDomain.getDomains().size());

        userService.findActiveUserByEmail("test@gmail.com").ifPresent(u -> {
            List<SharedItem> sharedItems = service.findAllSharedItemsByRecipientId(u.getId());
            assertTrue(CollectionUtils.isEmpty(sharedItems));
        });
        userService.findActiveUserByEmail("test@test.com").ifPresent(u -> {
            List<SharedItem> sharedItems = service.findAllSharedItemsByRecipientId(u.getId());
            assertTrue(CollectionUtils.isNotEmpty(sharedItems));
        });
        sharingController.deleteAllAllowedDomains();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD,DELETE_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboard(){
        Instant instant = Instant.now().plus(2, ChronoUnit.DAYS);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());

        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
        Optional<User> user = userService.findActiveUserByEmail("test@test.com");
        try{
            user.ifPresent(u -> {
                SyncariContext.setUser(u);
                List<InsightsSharedDashboardDTO> dtos = sharingController.findAllSharedDashboard();
                assertTrue(CollectionUtils.isEmpty(dtos));
            });
        }finally {
            SyncariContext.restore();
        }

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD,DELETE_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboardWithTodayExpiry(){
        Instant instant = Instant.now();
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        Date dateExpired = Date.from(Instant.ofEpochMilli(((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getExpiredTime()));
        assertEquals(Date.from(Instant.now().plus(7, ChronoUnit.DAYS)).getDate(),dateExpired.getDate());
        assertEquals(SharedItemInvitationStatus.NOT_OPENED.name(),((InsightsDashboardSharedItem) responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getInvitationStatus().name());
        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
        Optional<User> user = userService.findActiveUserByEmail("test@test.com");
        try{
            user.ifPresent(u -> {
                SyncariContext.setUser(u);
                List<InsightsSharedDashboardDTO> dtos = sharingController.findAllSharedDashboard();
                assertTrue(CollectionUtils.isEmpty(dtos));
            });
        }finally {
            SyncariContext.restore();
        }

    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD,DELETE_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboardWithNeverExpiry(){
        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        assertEquals(-1,((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getExpiredTime());

        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
        Optional<User> user = userService.findActiveUserByEmail("test@test.com");
        try{
            user.ifPresent(u -> {
                SyncariContext.setUser(u);
                List<InsightsSharedDashboardDTO> dtos = sharingController.findAllSharedDashboard();
                assertTrue(CollectionUtils.isEmpty(dtos));
            });
        }finally {
            SyncariContext.restore();
        }
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD,DELETE_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboardAndFindUser(){
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now().plus(2, ChronoUnit.DAYS), ZoneOffset.UTC);

        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        Optional<User> user = userService.findActiveUserByEmail("test@test1.com");
        try{
            user.ifPresent(u -> {
                SyncariContext.setUser(u);
                List<InsightsSharedDashboardDTO> dtos = sharingController.findAllSharedDashboard();
                assertFalse(CollectionUtils.isEmpty(dtos));
                assertEquals("test",dtos.stream().findFirst().get().getDashboardId());
            });
        }finally {
            SyncariContext.restore();
        }

        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD
            ,DELETE_SHARED_DASHBOARD_DETAILS,READ_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboardAndFindItem(){
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now().plus(2, ChronoUnit.DAYS), ZoneOffset.UTC);

        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        InsightsShareDetailsResponse response = sharingController.getSharingDetails("test",null, PageDirection.next.name(),2,null);
        assertNotNull(response);
        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD
            ,DELETE_SHARED_DASHBOARD_DETAILS,READ_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboardAndFindItemWithNeverExpiry(){
        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        InsightsShareDetailsResponse response = sharingController.getSharingDetails("test",null, PageDirection.next.name(),2,null);
        assertNotNull(response);
        assertTrue(response.getShareDetailsRecords().stream().findFirst().isPresent());
        assertNull(response.getShareDetailsRecords().stream().findFirst().get().getExpiryDate());
        assertNotNull(response.getShareDetailsRecords().stream().findFirst().get().getStatus());
        assertEquals(SharedItemInvitationStatus.NOT_OPENED,response.getShareDetailsRecords().stream().findFirst().get().getStatus());
        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD
            ,DELETE_SHARED_DASHBOARD_DETAILS,READ_SHARED_DASHBOARD_DETAILS,UPDATE_SHARED_DASHBOARD_EXPIRY})
    public void testShareDashboardAndFindItemAndUpdateExpiry(){
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now().plus(2, ChronoUnit.DAYS), ZoneOffset.UTC);

        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        sharingController.updateExpiry(responseDTOS.stream().findFirst().get().getSharedItem().getId(),ldt.plus(7, ChronoUnit.DAYS).toString());
        InsightsShareDetailsResponse response = sharingController.getSharingDetails("test",null, PageDirection.next.name(),2,null);
        assertNotNull(response);
        assertTrue(response.getShareDetailsRecords().stream().findFirst().isPresent());
        assertNotNull(response.getShareDetailsRecords().stream().findFirst().get().getExpiryDate());
        assertTrue(response.getShareDetailsRecords().stream().findFirst().get().getExpiryDate().toEpochMilli() > Instant.now().plus(2, ChronoUnit.DAYS).toEpochMilli());
        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_INSIGHTS,SHARE_DASHBOARD,READ_ALL_SHARED_DASHBOARD
            ,DELETE_SHARED_DASHBOARD_DETAILS,READ_SHARED_DASHBOARD_DETAILS})
    public void testShareDashboardAndFindItemAndReshare(){
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now().plus(2, ChronoUnit.DAYS), ZoneOffset.UTC);

        List<InsightsShareDashboardResponseDTO> responseDTOS = sharingController.sharedashboard(new InsightsSharingDashboardDTO()
                .setDashboardId("test").setEmails(List.of("test@test.com"))
                .setExpiryDate(ldt.toString()).setMessage("test"));
        assertNotNull(responseDTOS);
        assertFalse(CollectionUtils.isEmpty(responseDTOS));
        assertEquals(1,responseDTOS.size());
        assertNotNull(responseDTOS.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)responseDTOS.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",responseDTOS.stream().findFirst().get().getSharedItem().getSourceId());
        InsightsShareDetailsResponse response = sharingController.getSharingDetails("test",null, PageDirection.next.name(),2,null);
        assertNotNull(response);
        assertTrue(CollectionUtils.isNotEmpty(response.getShareDetailsRecords()));
        assertNotNull(response.getShareDetailsRecords().stream().findFirst().get().getSharedItemId());
        List<InsightsShareDashboardResponseDTO> reSharedItemList = sharingController.reshareDashboard(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
        assertNotNull(reSharedItemList);
        assertFalse(CollectionUtils.isEmpty(reSharedItemList));
        assertEquals(1,reSharedItemList.size());
        assertNotNull(reSharedItemList.stream().findFirst().get().getSharedItem().getItemObject());
        assertEquals("test",((InsightsDashboardSharedItem)reSharedItemList.stream().findFirst().get().getSharedItem().getItemObject()).getEmailMessage());
        assertEquals("test",reSharedItemList.stream().findFirst().get().getSharedItem().getSourceId());
        sharingController.deleteSharingDetails(List.of(responseDTOS.stream().findFirst().get().getSharedItem().getId()));
    }
}
