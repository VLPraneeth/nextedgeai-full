package com.syncari.dbm.customscripts.syncari;

import java.util.LinkedHashSet;
import java.util.Optional;

import com.syncari.core.model.misc.RoleConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_13105_CleanAvailableInstance {

    @ChangeSet(order = "001", id = "cleanAvailableInstance", author = "sibin", runAlways = true)
    public void cleanUserRolesFromOrg(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        UserRepo userRepo = MigrationContext.getUserRepo();
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        UserService userService = MigrationContext.getUserService();
        //Get username as parameter
        //Find the user and get available instance
        //remove available instance if user dont have roles in that instance
        //update the available instance list of the user
        var email = System.getProperty("user");
        log.info("Request received to cleanup available instance for {} ", email);
        userRepo.findByEmail(email).ifPresent(u ->{
        	var availableInstance = new LinkedHashSet<>(u.getAvailableInstances());
        	log.info("user {} has available instance {}", email, availableInstance);
        	Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
            userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
                        SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
                    });
            new LinkedHashSet<>(availableInstance).forEach(insId -> {
            	var org = organizationRepo.findBySyncariId(insId);
				if (org.isPresent()) {
					var o = org.get();
					SyncariContext.setOrganziation(o);
					var instance = o.getInstance(insId);
					if (instance.isPresent()) {
						var i = instance.get();
						SyncariContext.setInstance(i);
						var roles = userService.getUserRoleForInstance(u.getId(), i);
						if (roles.contains(RoleConstants.SUPER_ADMIN)){
							roles.remove(RoleConstants.SUPER_ADMIN);
						}
						if (CollectionUtils.isEmpty(roles)) {
							log.info("Instance {} will be removed from available instance for {}",
									i.getSyncariId(), email);
							availableInstance.remove(i.getSyncariId());
						}
					} else {
						log.info("Instance {} will be removed from available instance for {}",
								insId, email);
						availableInstance.remove(insId);
					}
        		} else {
        			log.info("Instance {} will be removed from available instance for {}",
        					insId, email);
					availableInstance.remove(insId);
        		}
        	});
        	log.info("Final available instances {}", availableInstance);
        	if(!dryRunMode) {
        		u.setAvailableInstances(availableInstance);
        		userRepo.save(u);
        	}
        });
    }
}
