package com.syncari.dbm.customscripts.syncari;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.repositories.syncari.UserRepo;
import com.syncari.core.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SYN_1635_CleanUserRolesFromOrg {

    @ChangeSet(order = "001", id = "cleanUserRolesFromOrg", author = "sibin", runAlways = true)
    public void cleanUserRolesFromOrg(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        UserRepo userRepo = MigrationContext.getUserRepo();
        OrganizationRepo organizationRepo = MigrationContext.getOrganizationRepo();
        UserService userService = MigrationContext.getUserService();
        //Get username as parameter
        //Get org name as parameter
        //If org name is all then find all orgs else find specific org
        //Find the user and get available and current instance
        //remove roles from each instance of each org (if not current org)
        //update the available instance list of the user
        var email = System.getProperty("user");
        var org = System.getProperty("org");
        log.info("Request received to cleanup roles for {} in organization {}", email, org);
        userRepo.findByEmail(email).ifPresent(u ->{
        	var currentInstance = u.getCurrentInstanceId();
        	var availableInstance = new LinkedHashSet<>(u.getAvailableInstances());
        	log.info("user {} has current instance {}", email, currentInstance);
        	log.info("user {} has available instance {}", email, availableInstance);
        	List<Organization> orgList = new ArrayList<>();
        	if("all".equals(org)) {
        		orgList.addAll(organizationRepo.findAll());
        	} else {
        		organizationRepo.findByName(org).ifPresent(o -> {
        			orgList.add(o);
        		});
        	}
        	var currentOrg = organizationRepo.findBySyncariId(currentInstance);
        	currentOrg.ifPresent(o -> {
        		log.info("Removing current org list {}", orgList.stream().map(o1 -> o1.getName()).collect(Collectors.toList()));
        		orgList.remove(o);
        	});
        	log.info("Role cleanup will be done on {}", orgList.stream().map(o1 -> o1.getName()).collect(Collectors.toList()));
        	
        	Optional<User> userToSetContext = userService.findActiveUserByEmail("systemuser@syncari.com");
            userToSetContext.ifPresentOrElse(usr -> SyncariContext.setUser(usr), () -> {
                        SyncariContext.setUser(userService.findActiveUserByEmail("system_syncari_admin@syncari.com").get());
                    });
        	orgList.forEach(o -> {
        		SyncariContext.setOrganziation(o);
        		var instances = o.getInstances();
        		if(CollectionUtils.isNotEmpty(instances)) {
        			instances.forEach(i -> {
        				SyncariContext.setInstance(i);
        				var roles = userService.getUserRoleForInstance(u.getId(), i);
        				log.info("roles {} will be removed from instance {} of {}", roles, i.getName(), o.getName());
        				if(!dryRunMode) {
        					userService.removeRolesFromUser(u, roles);
        				}
        				availableInstance.remove(i.getSyncariId());
        			});
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
