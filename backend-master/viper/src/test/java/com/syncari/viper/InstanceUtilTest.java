package com.syncari.viper;

import com.syncari.AbstractSyncariTest;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.fail;

@Ignore
public class InstanceUtilTest extends AbstractSyncariTest {

    @Autowired
    InstanceUtil instanceUtil;

    @Autowired
    OrganizationRepo organizationRepo;

    @Before
    public void setUp() {
        super.setUp();
        organizationRepo.findBySyncariId("test_org_instance").ifPresent(o -> {
            o.getInstance("test_org_instance").ifPresent(i -> {
                i.setStatus(Status.INACTIVE);
                organizationRepo.save(o);
            });
        });
    }
    @After
    public void teardown(){
        organizationRepo.findBySyncariId("test_org_instance").ifPresent(o -> {
            o.getInstance("test_org_instance").ifPresent(i -> {
                i.setStatus(Status.ACTIVE);
                organizationRepo.save(o);
            });
        });
        super.tearDown();
    }

    @Test
    public void testForEachInstance(){
        instanceUtil.forEachInstance(context -> {
            if (context.getInstance().getSyncariId().equalsIgnoreCase("test_org_instance")){
                fail();
            }
        });
    }
}
