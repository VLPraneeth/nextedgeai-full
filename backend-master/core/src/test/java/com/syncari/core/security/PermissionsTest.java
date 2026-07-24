package com.syncari.core.security;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class PermissionsTest {

    @Test
    public void dashboardAuthorRole(){
        List<String> dashAuthorPermissions = Permissions.dashboardAuthorPermissions();
        Assert.assertTrue(dashAuthorPermissions.containsAll(Permissions.getAllDashboardPermissions()));
        Assert.assertTrue(dashAuthorPermissions.containsAll(Permissions.viewerPermissions()));
    }
}
