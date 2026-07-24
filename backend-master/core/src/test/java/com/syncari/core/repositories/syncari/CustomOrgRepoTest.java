package com.syncari.core.repositories.syncari;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.Organization;
import com.syncari.core.model.util.Status;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.Assert.*;

public class CustomOrgRepoTest extends AbstractSyncariTest {
    @Autowired
    CustomOrganizationRepo customOrganizationRepo;

    @Test
    public void findAndModifyTest(){
        // There is no status set, this test is to validate query is running fine
        Optional<Organization> org = customOrganizationRepo.findAndModifyInstanceStatus("syncari_admin",Status.ACTIVE, Status.DELETED);
        assertFalse(org.isPresent());
        Optional<Organization> org1 = customOrganizationRepo.findAndModifyInstanceStatus("syncari_admin",Status.DELETED, Status.ACTIVE);
        assertFalse(org1.isPresent());
    }
}
