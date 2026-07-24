package com.syncari.core.repositories.syncari;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.syncari.core.model.GlobalConfiguration;

@Repository
public interface GlobalConfigurationRepo extends MongoRepository<GlobalConfiguration, String> {

	Optional<GlobalConfiguration> findByKey(String key);

	List<GlobalConfiguration> findAllByValue(String value);
}
