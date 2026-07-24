package com.syncari.core.repositories.syncari;

import com.syncari.core.model.Organization;
import com.syncari.core.repositories.SyncariRepo;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepo extends SyncariRepo<Organization> {
    Optional<Organization> findByName(String name);

    @Query("{'instances': {'$elemMatch':{'syncariId' : ?0}}}")
    Optional<Organization> findBySyncariId(String syncariId);

    @Query("{'instances': {'$elemMatch':{'syncariId' : {'$ne':'syncari_admin'}}}}")
    List<Organization> findAllCustomers();

    @Query("{'$or':[{'status':'ACTIVE'},{'status':null}],'instances': {'$elemMatch':{ $or:[{'status':'ACTIVE'},{'status':null}]}}}")
    List<Organization> findAllActiveCustomers();

    @Query("{'status':{'$ne':'HARD_DELETING'}, 'instances': {'$elemMatch': {'type': ?0}}}")
    List<Organization> findAllCustomersByInstanceType(String type);

    @Query("{'status':'DELETED'}")
    List<Organization> findDeletedCustomers();

    @Query("{'status':'HARD_DELETING'}")
    List<Organization> findHardDeletingCustomers();

    @Query("{'instances':{'$elemMatch' : {'status': 'DELETED'}}}")
    List<Organization> findDeletedInstancesOrg();

}
