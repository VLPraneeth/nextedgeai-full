package com.syncari.viper;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.syncari.core.model.Instance;
import com.syncari.core.model.Organization;
import com.syncari.core.model.User;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.syncari.OrganizationRepo;
import com.syncari.core.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class InstanceUtil {
    
    @Autowired
    OrganizationRepo organizationRepo;
    @Autowired
    UserService userService;
    
    public void forEachInstance(Consumer<ViperContext> operation) {
        List<Organization> all = organizationRepo.findAllActiveCustomers();
        for(Organization organization : all){
            User systemUser = userService.getSystemUser();
            List<Instance> instances = organization.getInstances();
            for(Instance instance : instances){
                Optional<Organization> org = organizationRepo.findBySyncariId(instance.getSyncariId());
                org.ifPresent(o -> {
                    o.getInstance(instance.getSyncariId()).ifPresentOrElse(i -> {
                        if ((i.getStatus() == null) ||  (i.getStatus().equals(Status.ACTIVE))) {
                            ViperContext ctx = ViperContext.of(organization, instance, systemUser);
                            ctx.with(() -> {
                                operation.accept(ctx);
                                return null;
                            });
                        }else{
                            log.error("Trying to do activity for not active Instance {}", i.getSyncariId());
                        }
                    },() -> log.error("Did not find syncariId {}", instance.getSyncariId()));
                });
            }
        }
    }
    /*
    public void forEachInstance(Runnable block) {
        List<Organization> all = organizationRepo.findAllActiveCustomers();
        for (Organization organization : all) {
            User systemUser = userService.getSystemUser(organization.getId());
            List<Instance> instances = organization.getInstances();
            for (Instance instance : instances) {
				SyncariContext.runWithContext(organization, instance, systemUser, block);
			}
		}
    }
    */
}
