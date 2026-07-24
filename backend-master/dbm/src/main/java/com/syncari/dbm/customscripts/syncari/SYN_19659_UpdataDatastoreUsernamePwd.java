package com.syncari.dbm.customscripts.syncari;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.connector.Constants;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.Resource;
import com.syncari.core.model.ResourceType;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SYN_19659_UpdataDatastoreUsernamePwd {

    public static final String DATASTORE_PASSWORD = "datastorePassword";
    public static final String DATASTORE_USER_NAME = "datastoreUserName";
    public static final String DATASTORE_NAME = "Syncari Datastore";


    // There is a script in customers directory SYN_19659_GeneratePostgresUsernamePwd to generate username and pwd, use that first and pass those here

    @ChangeSet(order = "001", id = "updateDatastoreUserNamePwd", author = "rohit", runAlways = true)
    public void updateDatastoreUserNamePwd(MongoTemplate template) {

        EncryptionService encryptionService = MigrationContext.getEncryptionService();

        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));

        String userName = System.getProperty("userName");
        String pwdToUse = System.getProperty("password");
        String syncariIdToUpdate = System.getProperty("syncariId");

        if ((null == syncariIdToUpdate) || (null == userName) || (null == pwdToUse)){
            throw new RuntimeException("One of input parameter orgIdsParam, pwdToUse, userName or syncariIdToUpdate is missing. Please pass all params");
        }
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        Optional<Organization> org = organizationRepo.findBySyncariId(syncariIdToUpdate);
        org.ifPresentOrElse(o -> {
            List<Instance> instanceList = o.getInstances();
            List<Instance> instanceToFixList  = instanceList.stream().filter(i -> i.getSyncariId().equalsIgnoreCase(syncariIdToUpdate)).collect(Collectors.toList());
            Optional<Instance> instanceToFix = instanceToFixList.stream().findFirst();
            instanceToFix.ifPresentOrElse(i -> {
                Resource resource = new Resource(ResourceType.DATASTORE);
                resource.getConfiguration().put(DATASTORE_USER_NAME, userName);
                resource.getConfiguration().put(DATASTORE_PASSWORD, encryptionService.encrypt(pwdToUse));
                resource.getConfiguration().put(Constants.DATABASE_NAME, "syncari_"+syncariIdToUpdate.toLowerCase());
                if (!dryRunMode){
                    log.info("Adding resource {} to org with id {}", resource, o.getId());
                    Map<ResourceType, Resource> resources = i.getResources();
                    resources.put(ResourceType.DATASTORE, resource);
                    organizationRepo.save(o);
                }else{
                    log.info("Running in dryRunMode, not adding resource {}", resource);
                }

            }, () -> log.info("Instance with syncariId {} does not exists",syncariIdToUpdate));
        },() -> log.info("Org for syncariId {} does not exists", syncariIdToUpdate));




    }
}
