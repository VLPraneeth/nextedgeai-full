package com.syncari.core.repositories.syncari;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.misc.InstanceType;
import com.syncari.core.model.util.Status;

public class OrganizationRepoTest extends AbstractSyncariTest {

    @Autowired
    OrganizationRepo organizationRepo;
    @Test
    public void findActive(){
        Organization activeOrgAndInstance = new Organization();
        activeOrgAndInstance.setStatus(Status.ACTIVE);
        activeOrgAndInstance.setInstances(List.of(newInstance(Status.ACTIVE)));
        activeOrgAndInstance.setName("ActiveTest1");

        Organization inactiveOrg = new Organization();
        inactiveOrg.setStatus(Status.INACTIVE);
        inactiveOrg.setName("ActiveTest2");

        Organization noStatusOrg = new Organization();
        noStatusOrg.setName("ActiveTest3");
        noStatusOrg.setInstances(List.of(newInstance(null)));

        Organization activeOrgAndInactiveInstance = new Organization();
        activeOrgAndInactiveInstance.setStatus(Status.ACTIVE);
        activeOrgAndInactiveInstance.setInstances(List.of(newInstance(Status.INACTIVE)));
        activeOrgAndInactiveInstance.setName("ActiveTest4");

        Organization activeOrgAndOneInactiveInstance = new Organization();
        activeOrgAndOneInactiveInstance.setStatus(Status.ACTIVE);
        activeOrgAndOneInactiveInstance.setInstances(List.of(newInstance(Status.ACTIVE), newInstance(Status.INACTIVE)));
        activeOrgAndOneInactiveInstance.setName("ActiveTest5");

        organizationRepo.saveAll(List.of(activeOrgAndInstance,inactiveOrg, noStatusOrg,activeOrgAndInactiveInstance, activeOrgAndOneInactiveInstance));
        List<Organization> allActiveCustomers = organizationRepo.findAllActiveCustomers();
        //3 from above + 1 from current context
        Set<String> names = allActiveCustomers.stream().map(o->o.getName()).collect(Collectors.toSet());
        assertTrue(allActiveCustomers.size() > 4);
        assertTrue(names.contains("ActiveTest1"));
        assertTrue(names.contains("ActiveTest3"));
        assertTrue(names.contains("ActiveTest5"));

        assertFalse(names.contains("ActiveTest2"));
        assertFalse(names.contains("ActiveTest4"));
        //filter current context

//        assertEquals(Set.of("ActiveTest1","ActiveTest3","ActiveTest5","Syncari Master",SyncariContext.getOrganziation().getName()), names);

    }

    private Instance newInstance(Status status) {
        Instance activeInstance1 = new Instance(UUID.randomUUID().toString(), "test");
        if(status!=null) {
            activeInstance1.setStatus(status);
        }
        activeInstance1.setType(InstanceType.production);
        return activeInstance1;
    }
}