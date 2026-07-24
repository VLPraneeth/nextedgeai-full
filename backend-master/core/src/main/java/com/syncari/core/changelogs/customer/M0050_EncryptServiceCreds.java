package com.syncari.core.changelogs.customer;

import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;

import lombok.extern.slf4j.Slf4j;

@ChangeLog(order = "0050")
@Slf4j
public class M0050_EncryptServiceCreds {

    @ChangeSet(order = "001", id = "encryptServiceCreds", author = "varsha")
    public void encryptServiceCreds(MongoTemplate template) {
        MigrationContext.getProvisioningService().getRawCredentials().stream()
                .forEach(c -> MigrationContext.getProvisioningService().addServiceCredential(c));
    }
}
