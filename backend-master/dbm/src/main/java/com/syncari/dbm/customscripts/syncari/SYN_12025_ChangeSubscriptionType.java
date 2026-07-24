package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.misc.OrganizationType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Slf4j
public class SYN_12025_ChangeSubscriptionType {

    @ChangeSet(order = "001", id = "changeSubscriptionType", author = "varsha", runAlways = true)
    public void changeSubscriptionType(MongoTemplate template) {
        String orgIdsParam = System.getProperty("orgIds");
        if(StringUtils.isBlank(orgIdsParam)) return;
        String[] orgIds = orgIdsParam.split(",");
        Iterable<Organization> orgs = MigrationContext.getOrganizationRepo().findAllById(Arrays.asList(orgIds));
        List<Organization> updated = new ArrayList<>();
        orgs.forEach(o -> {
            Organization next = orgs.iterator().next();
            log.info("Updating org type to partner {} {}", next.getName(), next.getId());
            o.setOrgType(OrganizationType.partner);
            updated.add(o);
        });
        MigrationContext.getOrganizationRepo().saveAll(updated);
    }
}
