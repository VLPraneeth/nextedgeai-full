package com.syncari.core.repositories.customer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.SyncariRepo;

public interface TagRepo extends SyncariRepo<Tag> {
	List<Tag> findByNameAndTaggedId(String name, String taggedId);

	List<Tag> findByName(String name);

	List<Tag> findByTaggable(Taggable taggable);

	List<Tag> findByTaggableAndTaggedId(Taggable taggable, String taggedId);

	List<Tag> deleteByTaggableAndTaggedId(Taggable taggable, String taggedId);
	
	List<Tag> findByTaggableAndTaggedIdIn(Taggable taggable, Set<String> taggedIds);
	
	Optional<Tag> findByNameAndTaggableAndTaggedId(String name, Taggable taggable, String taggedId);
	
	@Query("{ 'name' : {$regex: ?0 } }")
	List<Tag> findByNameLike(String namePrefix);
}
