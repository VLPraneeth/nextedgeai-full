package com.syncari.core.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.Query;

import com.syncari.core.model.misc.DraftableModel;

public interface DraftableRepo<T extends DraftableModel> extends SyncariRepo<T> {
	List<T> findAllByParentId(String parentId);

	@Query("{ 'parentId' : ?0, 'draftStatus':'NEW', 'versionInfo':null}")
	Optional<T> findActiveDraftFor(String parentId);

}
