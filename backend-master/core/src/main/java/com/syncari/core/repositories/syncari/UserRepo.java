package com.syncari.core.repositories.syncari;

import com.syncari.core.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepo extends MongoRepository<User, String> {
	Optional<User> findByEmail(String email);

	@Query("{ 'email' : ?0, 'status':'ACTIVE'}")
	Optional<User> findByActiveByEmail(String email);

	@Query("{'status':'ACTIVE', 'systemUser' : false}")
	List<User> findAllActive();

	@Query("{'status':'ACTIVE', '$or':[{'isSuperAdmin':true},{'isGhostUser':true}]}")
	List<User> findAllActiveSystemUser();

	@Query("{'status':'ACTIVE', 'systemUser' : false, '$or':[{'userType':{'$exists' : false}},{'userType':'STANDARD'}]}")
	List<User> findAllActiveStandard();

	@Query("{ 'systemUser' : true, 'availableInstances' : {'$elemMatch': {'$in' :?0}}}")
	List<User> findSystemUserByAvailableInstances(Set<String> syncariIds);
	
	@Query("{ 'availableInstances' : {'$elemMatch': {'$in' :?0}}}")
	List<User> findUsersByAvailableInstances(Set<String> syncariIds);

	@Query("{ 'isSuperAdmin' : true }")
	List<User> findAllSuperAdmins();

	@Query("{ 'isGhostUser' : true }")
	List<User> findAllGhosts();

	@Query("{ 'clientId' : ?0, 'status':'ACTIVE'}")
	Optional<User> findByClientId(String clientId);

	@Query("{ 'oauthServices.clientId' : ?0}")
	List<User> findByOAuthClientId(String clientId);

}
